package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.LtaApiResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LTAService {

    private static final String ENDPOINT_PATH = "/v4/TrafficSpeedBands";
    private static final int PAGE_SIZE = 500;
    private final JdbcTemplate jdbcTemplate;

    // Limits num of concurrent calls made to the API
    private static final int BATCH_SIZE = 15;
    private final ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);

    private final RestTemplate ltaRestTemplate;
    private final RoadRepository roadRepository;

    @Value("${lta.datamall.base-url}")
    private String baseUrl;

    @Value("${lta.datamall.api-key}")
    private String apiKey;

    // 1. API Response Cache
    private List<LtaTrafficSpeedBandResponseDTO> cachedBands = null;
    private long cacheExpiresAt = 0;
    private static final long CACHE_TTL_MS = 2 * 60 * 1000;

    // 2. Database State Cache (for efficient DB updates)
    private final Long2IntOpenHashMap dbSpeedCache = new Long2IntOpenHashMap();
    private boolean isDbCacheLoaded = false;

    // 3. Database State Flags (prevents redundant I/O queries)
    private boolean isDbPopulated = false;
    private boolean hasCheckedDbPopulation = false;

    /**
     * This function asynchronously fetches all speed band info from the LTA API
     * Synchronized to prevent race conditions on the cache map when the TTL expires.
     *
     * @return Speed band information for all roads
     */
    public synchronized List<LtaTrafficSpeedBandResponseDTO> getAllSpeedBands() {

        long now = System.currentTimeMillis();
        if (cachedBands != null && now < cacheExpiresAt) { // cache "expires" after some time to ensure data integrity; forces update
            return cachedBands; // instant return, no HTTP calls
        }

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        List<LtaTrafficSpeedBandResponseDTO> firstPage = fetchPage(0, entity);

        // --- EARLY ERROR HANDLER: Check for silent failures ---
        if (firstPage == null || firstPage.isEmpty()) {
            log.error("First page from LTA API returned 0 results. This suggests an upstream API error.");
            throw new IllegalStateException("LTA API returned an empty payload on the first page.");
        }

        if (firstPage.size() < PAGE_SIZE) {
            return firstPage;
        }

        List<LtaTrafficSpeedBandResponseDTO> results = new ArrayList<>(firstPage);
        int currentSkip = PAGE_SIZE;
        boolean hasMoreData = true;
        int maxPagesSafeGuard = 600;
        int pagesFetched = 1;

        while (hasMoreData && pagesFetched < maxPagesSafeGuard) {
            List<CompletableFuture<List<LtaTrafficSpeedBandResponseDTO>>> batchFutures = new ArrayList<>();

            for (int i = 0; i < BATCH_SIZE; i++) {
                final int skipParam = currentSkip;
                batchFutures.add(CompletableFuture.supplyAsync(
                        () -> fetchPage(skipParam, entity), executor));
                currentSkip += PAGE_SIZE;
                pagesFetched++;
            }

            for (CompletableFuture<List<LtaTrafficSpeedBandResponseDTO>> future : batchFutures) {
                List<LtaTrafficSpeedBandResponseDTO> pageData = future.join();

                if (pageData.isEmpty()) {
                    hasMoreData = false;
                    break;
                }

                results.addAll(pageData);

                if (pageData.size() < PAGE_SIZE) {
                    hasMoreData = false;
                    break; // Last page reached
                }
            }
        }

        log.info("Total traffic speed band records fetched concurrently: {}", results.size());

        // Only query the DB once per application lifecycle to check if it's empty
        if (!hasCheckedDbPopulation) {
            isDbPopulated = roadRepository.count() > 0;
            hasCheckedDbPopulation = true;
        }

        if (!isDbPopulated) {
            // Database is completely empty, do the initial bulk load
            persistRoadSegmentsIfEmpty(results);

            for (LtaTrafficSpeedBandResponseDTO dto : results) {
                dbSpeedCache.put(Long.parseLong(dto.getLinkId()), dto.getSpeedBand());
            }
            isDbCacheLoaded = true;
            isDbPopulated = true; // Mark as populated for future runs
        } else {
            // Database is populated, perform diff update
            // Diffing eliminates redundant update operations (updating a record without actually changing any values)
            if (!isDbCacheLoaded) {
                warmUpDbCache();
            }
            performEfficientSpeedBandUpdate(results);
        }

        cachedBands = results;
        cacheExpiresAt = now + CACHE_TTL_MS;
        return results;
    }

    private List<LtaTrafficSpeedBandResponseDTO> fetchPage(int skip, HttpEntity<Void> entity) {
        String url = baseUrl + ENDPOINT_PATH + "?$skip=" + skip;
        log.debug("Fetching traffic speed bands: skip={}", skip);

        try {
            ResponseEntity<LtaApiResponseDTO> response = ltaRestTemplate.exchange(
                    url, HttpMethod.GET, entity, LtaApiResponseDTO.class);

            LtaApiResponseDTO body = response.getBody();
            if (body == null || body.getValue() == null) {
                log.warn("API response body or value array is null at skip={}", skip);
                return List.of();
            }

            return body.getValue();
        } catch (RestClientException e) {
            log.error("Error fetching page at skip={}: {}", skip, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch traffic speed bands from LTA DataMall at skip=" + skip, e);
        }
    }

    /**
     * This populates the local database with road segments if not already populated.
     * This will happen at most once (typically on initial loading of the web application).
     */
    public void persistRoadSegmentsIfEmpty(List<LtaTrafficSpeedBandResponseDTO> data) {
        String roadSql = "INSERT INTO roads (id, road_name, road_category, start_lat, start_lon, end_lat, end_lon) VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(roadSql, data, 1000, (ps, dto) -> {
            ps.setLong(1, Long.parseLong(dto.getLinkId()));
            ps.setString(2, dto.getRoadName());
            ps.setString(3, dto.getRoadCategory());
            ps.setDouble(4, dto.getStartLat());
            ps.setDouble(5, dto.getStartLon());
            ps.setDouble(6, dto.getEndLat());
            ps.setDouble(7, dto.getEndLon());
        });

        String speedBandSql = "INSERT INTO roadspeedbands (id, speed_band) VALUES (?, ?)";
        jdbcTemplate.batchUpdate(speedBandSql, data, 1000, (ps, dto) -> {
            ps.setLong(1, Long.parseLong(dto.getLinkId()));
            ps.setInt(2, dto.getSpeedBand());
        });

        log.info("Persisted {} road segments via batch insert.", data.size());
    }

    /**
     * Loads the current DB state into memory to enable efficient diffing.
     */
    private void warmUpDbCache() {
        log.info("Warming up database speed cache...");
        jdbcTemplate.query("SELECT id, speed_band FROM roadspeedbands", rs -> {
            dbSpeedCache.put(rs.getLong("id"), rs.getInt("speed_band"));
        });
        isDbCacheLoaded = true;
        log.info("Loaded {} records into database speed cache.", dbSpeedCache.size());
    }

    /**
     * Filters the API response to find only records that have changed,
     * and batches them into a single UPSERT transaction.
     */
    private void performEfficientSpeedBandUpdate(List<LtaTrafficSpeedBandResponseDTO> data) {
        List<LtaTrafficSpeedBandResponseDTO> changes = new ArrayList<>();

        for (LtaTrafficSpeedBandResponseDTO dto : data) {
            long id = Long.parseLong(dto.getLinkId());
            // If the road is completely new, or the speed band changed
            if (!dbSpeedCache.containsKey(id) || dbSpeedCache.get(id) != dto.getSpeedBand()) {
                changes.add(dto);
            }
        }

        if (changes.isEmpty()) {
            log.info("No traffic speed changes detected for DB update.");
            return;
        }

        // ON CONFLICT (id) DO UPDATE guarantees insertion of a newly discovered road, or update an existing one
        String sql = "INSERT INTO roadspeedbands (id, speed_band) VALUES (?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET speed_band = EXCLUDED.speed_band";

        jdbcTemplate.batchUpdate(sql, changes, 1000, (ps, dto) -> {
            long id = Long.parseLong(dto.getLinkId());
            ps.setLong(1, id);
            ps.setInt(2, dto.getSpeedBand());

            // Immediately update the cache so the next API poll is accurate
            dbSpeedCache.put(id, dto.getSpeedBand());
        });

        log.info("Persisted {} speed band changes to DB.", changes.size());
    }

    public void populateIfEmpty() {
        if (!hasCheckedDbPopulation) {
            isDbPopulated = roadRepository.count() > 0;
            hasCheckedDbPopulation = true;
        }

        if (isDbPopulated) {
            log.info("Road segments already populated, skipping LTA API fetch.");
            return;
        }
        log.info("Road segments empty, fetching from LTA API...");
        getAllSpeedBands();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccountKey", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
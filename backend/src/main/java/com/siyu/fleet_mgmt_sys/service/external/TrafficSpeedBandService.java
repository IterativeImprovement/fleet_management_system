package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.LtaApiResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
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
public class TrafficSpeedBandService {

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


    /**
     * This function asynchronously fetches all speed band info from the LTA API
     * @return Speed band information for all roads
     */

    public List<LtaTrafficSpeedBandResponseDTO> getAllSpeedBands() {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        List<LtaTrafficSpeedBandResponseDTO> firstPage = fetchPage(0, entity);
        if (firstPage.isEmpty() || firstPage.size() < PAGE_SIZE) {
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

        // stores road segments into Database if database is empty
        persistRoadSegmentsIfEmpty(results);
        return results;
    }

    private List<LtaTrafficSpeedBandResponseDTO> fetchPage(int skip, HttpEntity<Void> entity) {
        String url = baseUrl + ENDPOINT_PATH + "?$skip=" + skip;
        log.debug("Fetching traffic speed bands: skip={}", skip);

        try {
            ResponseEntity<LtaApiResponseDTO> response = ltaRestTemplate.exchange(
                    url, HttpMethod.GET, entity, LtaApiResponseDTO.class);

            LtaApiResponseDTO body = response.getBody();
            if (body == null || body.getValue() == null) return List.of();

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
        if (roadRepository.count() > 0) {
            return;
        }

        String sql = "INSERT INTO roads (id, road_name, road_category, start_lat, start_lon, end_lat, end_lon) VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, data, 1000, (ps, dto) -> {
            ps.setLong(1, Long.parseLong(dto.getLinkId()));
            ps.setString(2, dto.getRoadName());
            ps.setString(3, dto.getRoadCategory());
            ps.setDouble(4, dto.getStartLat());
            ps.setDouble(5, dto.getStartLon());
            ps.setDouble(6, dto.getEndLat());
            ps.setDouble(7, dto.getEndLon());
        });

        log.info("Persisted {} road segments via batch insert.", data.size());
    }

    public void populateIfEmpty() {
        if (roadRepository.count() > 0) {
            log.info("Road segments already populated, skipping LTA API fetch.");
            return;
        }
        log.info("Road segments empty, fetching from LTA API...");
        getAllSpeedBands();
    }
//    public List<LtaTrafficSpeedBandResponseDTO> getSpeedBandsByCategory(String category) {
//        return getAllSpeedBands().stream()
//                .filter(band -> category.equalsIgnoreCase(band.getRoadCategory()))
//                .toList();
//    }
//
//    public List<LtaTrafficSpeedBandResponseDTO> getSpeedBandsByBandNumber(int bandNumber) {
//        if (bandNumber < 1 || bandNumber > 8) {
//            throw new IllegalArgumentException("Speed band must be between 1 and 8");
//        }
//        return getAllSpeedBands().stream()
//                .filter(band -> band.getSpeedBand() == bandNumber)
//                .toList();
//    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccountKey", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
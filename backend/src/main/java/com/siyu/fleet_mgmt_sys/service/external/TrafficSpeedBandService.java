package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.LtaApiResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.model.RoadSegment;
import com.siyu.fleet_mgmt_sys.repository.RoadSegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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

    // Limits num of concurrent calls made to the API
    private static final int BATCH_SIZE = 15;
    private final ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);

    private final RestTemplate ltaRestTemplate;
    private final RoadSegmentRepository roadSegmentRepository;

    @Value("${lta.datamall.base-url}")
    private String baseUrl;

    @Value("${lta.datamall.api-key}")
    private String apiKey;

    private List<LtaTrafficSpeedBandResponseDTO> cachedBands = null;
    private long cacheExpiresAt = 0;
    private static final long CACHE_TTL_MS = 2 * 60 * 1000;

    /**
     * This function asynchronously fetches all speed band info from the LTA API
     * 
     * @return Speed band information for all roads
     */

    public List<LtaTrafficSpeedBandResponseDTO> getAllSpeedBands() {

        long now = System.currentTimeMillis();
        if (cachedBands != null && now < cacheExpiresAt) {
            return cachedBands; // instant return, no HTTP calls
        }

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
            if (body == null || body.getValue() == null)
                return List.of();

            return body.getValue();
        } catch (RestClientException e) {
            log.error("Error fetching page at skip={}: {}", skip, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch traffic speed bands from LTA DataMall at skip=" + skip, e);
        }
    }

    /**
     * This populates the local database with road segments if not already
     * populated.
     * This will happen at most once (typically on initial loading of the web
     * application).
     */

    private void persistRoadSegmentsIfEmpty(List<LtaTrafficSpeedBandResponseDTO> data) {
        if (roadSegmentRepository.count() > 0) {
            return;
        }

        List<RoadSegment> segments = data.stream()
                .map(dto -> {
                    RoadSegment seg = new RoadSegment();
                    seg.setId(Long.parseLong(dto.getLinkId())); // converts string to long
                    seg.setRoadName(dto.getRoadName());
                    seg.setRoadCategory(dto.getRoadCategory());
                    seg.setStartLat(dto.getStartLat());
                    seg.setStartLon(dto.getStartLon());
                    seg.setEndLat(dto.getEndLat());
                    seg.setEndLon(dto.getEndLon());
                    return seg;
                })
                .toList();

        roadSegmentRepository.saveAll(segments);
        log.info("Persisted {} road segments.", segments.size());
    }

    // public List<LtaTrafficSpeedBandResponseDTO> getSpeedBandsByCategory(String
    // category) {
    // return getAllSpeedBands().stream()
    // .filter(band -> category.equalsIgnoreCase(band.getRoadCategory()))
    // .toList();
    // }
    //
    // public List<LtaTrafficSpeedBandResponseDTO> getSpeedBandsByBandNumber(int
    // bandNumber) {
    // if (bandNumber < 1 || bandNumber > 8) {
    // throw new IllegalArgumentException("Speed band must be between 1 and 8");
    // }
    // return getAllSpeedBands().stream()
    // .filter(band -> band.getSpeedBand() == bandNumber)
    // .toList();
    // }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccountKey", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
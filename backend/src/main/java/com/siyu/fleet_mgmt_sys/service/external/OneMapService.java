package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.*;
import com.siyu.fleet_mgmt_sys.model.Location;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.LocationSource;
import com.siyu.fleet_mgmt_sys.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OneMapService {

    private static final String BASE_URL = "https://www.onemap.gov.sg";
    private static final String GET_ALL_THEMES = "/api/public/themesvc/getAllThemesInfo?moreInfo=Y";
    private static final String RETRIEVE_THEME = "/api/public/themesvc/retrieveTheme?queryName=";
    private static final int BATCH_SIZE = 10;

    private final RestTemplate restTemplate;
    private final LocationRepository locationRepository;

    @Value("${onemap.api-key}")
    private String apiKey;

    /* Location / Theme */

    public List<Location> getAllLocations() {
        populateIfEmpty();
        return locationRepository.findAllBySource(LocationSource.ONEMAP);
    }

    public void populateIfEmpty() {
        if (locationRepository.countBySource(LocationSource.ONEMAP) > 0) {
            log.info("OneMap locations already populated, skipping.");
            return;
        }

        log.info("Fetching all OneMap themes...");
        List<String> queryNames = fetchAllThemeQueryNames();
        log.info("Found {} themes", queryNames.size());

        List<Location> allLocations = fetchAllLocations(queryNames);
        locationRepository.saveAll(allLocations);
        log.info("Saved {} OneMap locations", allLocations.size());
    }

    private List<String> fetchAllThemeQueryNames() {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        try {
            ResponseEntity<OneMapThemeInfoResponse> response = restTemplate.exchange(
                    BASE_URL + GET_ALL_THEMES,
                    HttpMethod.GET,
                    entity,
                    OneMapThemeInfoResponse.class);

            OneMapThemeInfoResponse body = response.getBody();
            if (body == null || body.getThemeNames() == null) return List.of();

            return body.getThemeNames().stream()
                    .map(OneMapThemeDTO::getQueryName)
                    .filter(q -> q != null && !q.isBlank())
                    .toList();
        } catch (RestClientException e) {
            log.error("Failed to fetch OneMap themes: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Location> fetchAllLocations(List<String> queryNames) {
        ExecutorService executor = Executors.newFixedThreadPool(BATCH_SIZE);
        List<Location> allLocations = Collections.synchronizedList(new ArrayList<>());
        Set<String> seenNames = Collections.synchronizedSet(new HashSet<>());

        List<CompletableFuture<Void>> futures = queryNames.stream()
                .map(queryName -> CompletableFuture.runAsync(() -> {
                    List<Location> locations = fetchLocationsForTheme(queryName);
                    for (Location loc : locations) {
                        if (seenNames.add(loc.getName())) {
                            allLocations.add(loc);
                        }
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        return allLocations;
    }

    private List<Location> fetchLocationsForTheme(String queryName) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        try {
            ResponseEntity<OneMapThemeResultResponse> response = restTemplate.exchange(
                    BASE_URL + RETRIEVE_THEME + queryName,
                    HttpMethod.GET,
                    entity,
                    OneMapThemeResultResponse.class);

            OneMapThemeResultResponse body = response.getBody();
            if (body == null || body.getSrchResults() == null) return List.of();

            return body.getSrchResults().stream()
                    .filter(dto -> "Point".equals(dto.getType()))
                    .filter(dto -> dto.getName() != null && dto.getLatLng() != null)
                    .map(this::toLocation)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (RestClientException e) {
            log.warn("Failed to fetch theme {}: {}", queryName, e.getMessage());
            return List.of();
        }
    }

    private Location toLocation(OneMapLocationDTO dto) {
        try {
            String[] parts = dto.getLatLng().split(",");
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());

            String address = (dto.getBlockNumber() != null ? dto.getBlockNumber() + " " : "")
                    + (dto.getStreetName() != null ? dto.getStreetName() : "");

            return Location.builder()
                    .name(dto.getName())
                    .address(address.trim())
                    .postalCode(dto.getPostalCode())
                    .latitude(lat)
                    .longitude(lng)
                    .source(LocationSource.ONEMAP)
                    .externalId(dto.getName())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse location {}: {}", dto.getName(), e.getMessage());
            return null;
        }
    }

    /* Routing */

    public OneMapRouteResponseDTO getRoute(String start, String end, String... blockages) {
        WayPoint startWp = new WayPoint(start);
        WayPoint endWp = new WayPoint(end);
        return getOneMapRouteResponseDTO(startWp, endWp);
    }

    public OneMapRouteResponseDTO getRoute(WayPoint startWp, WayPoint endWp, String... blockages) {
        return getOneMapRouteResponseDTO(startWp, endWp);
    }

    public List<OneMapSearchResponseDTO.SearchResult> searchLocations(String searchValue) {
        String url = UriComponentsBuilder
                .fromUriString(BASE_URL + "/api/common/elastic/search")
                .queryParam("searchVal", searchValue)
                .queryParam("returnGeom", "Y")
                .queryParam("getAddrDetails", "Y")
                .queryParam("pageNum", 1)
                .build()
                .encode()
                .toUriString();

        ResponseEntity<OneMapSearchResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                buildBearerEntity(),
                OneMapSearchResponseDTO.class);

        OneMapSearchResponseDTO body = response.getBody();
        if (body == null || body.getResults() == null) return List.of();
        return body.getResults();
    }

    @Nullable
    private OneMapRouteResponseDTO getOneMapRouteResponseDTO(WayPoint startWp, WayPoint endWp) {
        String url = BASE_URL + "/api/public/routingsvc/route" +
                "?routeType=drive" +
                "&start=" + startWp.getLatitude() + "," + startWp.getLongitude() +
                "&end=" + endWp.getLatitude() + "," + endWp.getLongitude();

        ResponseEntity<OneMapRouteResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                buildBearerEntity(),
                OneMapRouteResponseDTO.class);

        log.info("Route retrieved: {} -> {}", startWp, endWp);
        return response.getBody();
    }

    /* Helpers */

    // Used for theme/location endpoints
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    // Used for routing/search endpoints
    private @NonNull HttpEntity<String> buildBearerEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        return new HttpEntity<>(headers);
    }
}
package com.siyu.fleet_mgmt_sys.service.location;

import com.siyu.fleet_mgmt_sys.dto.LocationRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.LocationResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.OneMapLocationRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.OneMapSearchResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.CustomLocationRenameRequiredException;
import com.siyu.fleet_mgmt_sys.exception.LocationNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Location;
import com.siyu.fleet_mgmt_sys.model.enums.LocationSource;
import com.siyu.fleet_mgmt_sys.repository.LocationRepository;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private static final double MIN_LATITUDE = 1.1304;
    private static final double MAX_LATITUDE = 1.4504;
    private static final double MIN_LONGITUDE = 103.6020;
    private static final double MAX_LONGITUDE = 104.0850;

    private final LocationRepository locationRepository;
    private final OneMapService oneMapService;

    @Transactional(readOnly = true)
    public List<LocationResponseDTO> searchLocations(String query) {
        String trimmedQuery = cleanRequired(query, "Search query is required.");
        List<String> searchQueries = buildLocationSearchQueries(trimmedQuery);
        Map<String, LocationResponseDTO> locationsByKey = new LinkedHashMap<>();

        searchQueries.forEach(searchQuery -> locationRepository
                    .findTop10ByNameContainingIgnoreCaseOrAddressContainingIgnoreCaseOrPostalCodeContainingIgnoreCase(
                            searchQuery,
                            searchQuery,
                            searchQuery
                    )
                    .forEach(location -> locationsByKey.put(locationKey(location), new LocationResponseDTO(location))));

        searchQueries.forEach(searchQuery -> addOneMapSearchResults(searchQuery, locationsByKey));

        return locationsByKey.values().stream()
                .sorted(Comparator.comparingInt(
                        (LocationResponseDTO location) -> relevanceScore(location, trimmedQuery)
                ).reversed())
                .toList();
    }

    private void addOneMapSearchResults(String query, Map<String, LocationResponseDTO> locationsByKey) {
        try {
            oneMapService.searchLocations(query).stream()
                    .map(this::toOneMapLocationResponse)
                    .forEach(location -> {
                        if (location != null) {
                            locationsByKey.putIfAbsent(locationKey(location), location);
                        }
                    });
        } catch (Exception ex) {
            log.warn("OneMap location search failed for '{}': {}", query, ex.getMessage());
        }
    }

    @Transactional
    public LocationResponseDTO createCustomLocation(LocationRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Location request is required.");
        }

        String name = cleanRequired(request.getName(), "Location name is required.");
        double latitude = requireCoordinate(request.getLatitude(), "Latitude is required.");
        double longitude = requireCoordinate(request.getLongitude(), "Longitude is required.");
        validateSingaporeBounds(latitude, longitude);

        String address = cleanOptional(request.getAddress());
        String postalCode = cleanOptional(request.getPostalCode());
        String externalId = buildCustomExternalId(latitude, longitude);
        Location location = locationRepository
                .findBySourceAndExternalId(LocationSource.CUSTOM, externalId)
                .orElseGet(() -> locationRepository
                        .findFirstBySourceAndLatitudeAndLongitude(LocationSource.CUSTOM, latitude, longitude)
                        .orElse(null));

        if (location != null) {
            boolean isRename = !location.getName().equals(name);
            if (isRename && !Boolean.TRUE.equals(request.getConfirmRename())) {
                throw new CustomLocationRenameRequiredException(new LocationResponseDTO(location), name);
            }

            location.setName(name);
            if (address != null) {
                location.setAddress(address);
            }
            if (postalCode != null) {
                location.setPostalCode(postalCode);
            }
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setSource(LocationSource.CUSTOM);
            location.setExternalId(externalId);

            return new LocationResponseDTO(locationRepository.save(location));
        }

        location = new Location();
        location.setName(name);
        location.setAddress(address);
        location.setPostalCode(postalCode);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setSource(LocationSource.CUSTOM);
        location.setExternalId(externalId);

        return new LocationResponseDTO(locationRepository.save(location));
    }

    @Transactional
    public LocationResponseDTO saveSelectedOneMapLocation(OneMapLocationRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("OneMap location request is required.");
        }

        String name = cleanRequired(request.getName(), "Location name is required.");
        double latitude = requireCoordinate(request.getLatitude(), "Latitude is required.");
        double longitude = requireCoordinate(request.getLongitude(), "Longitude is required.");
        validateSingaporeBounds(latitude, longitude);

        String postalCode = cleanNil(request.getPostalCode());
        String externalId = cleanOptional(request.getExternalId());
        if (externalId == null) {
            externalId = buildOneMapExternalId(name, postalCode, latitude, longitude);
        }

        Location location = locationRepository
                .findBySourceAndExternalId(LocationSource.ONEMAP, externalId)
                .orElseGet(Location::new);

        location.setName(name);
        location.setAddress(cleanNil(request.getAddress()));
        location.setPostalCode(postalCode);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setSource(LocationSource.ONEMAP);
        location.setExternalId(externalId);

        return new LocationResponseDTO(locationRepository.save(location));
    }

    @Transactional(readOnly = true)
    public Location getLocationOrThrow(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Location id is required.");
        }

        return locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public LocationResponseDTO getLocation(Long id) {
        return new LocationResponseDTO(getLocationOrThrow(id));
    }

    private LocationResponseDTO toOneMapLocationResponse(OneMapSearchResponseDTO.SearchResult result) {
        if (result == null) return null;

        Double latitude = parseCoordinate(result.getLatitude());
        Double longitude = parseCoordinate(result.getLongitude());
        if (latitude == null || longitude == null || !isWithinSingapore(latitude, longitude)) {
            return null;
        }

        String name = firstPresent(result.getSearchVal(), result.getBuilding(), result.getAddress());
        if (name == null) return null;

        String postalCode = cleanNil(result.getPostal());
        String externalId = buildOneMapExternalId(name, postalCode, latitude, longitude);

        return locationRepository
                .findBySourceAndExternalId(LocationSource.ONEMAP, externalId)
                .map(LocationResponseDTO::new)
                .orElseGet(() -> new LocationResponseDTO(
                        null,
                        name,
                        cleanNil(result.getAddress()),
                        postalCode,
                        latitude,
                        longitude,
                        LocationSource.ONEMAP.name(),
                        externalId
                ));
    }

    private String locationKey(Location location) {
        if (location.getSource() != null && location.getExternalId() != null) {
            return location.getSource() + ":" + location.getExternalId();
        }

        return "id:" + location.getId();
    }

    private String locationKey(LocationResponseDTO location) {
        if (location.getSource() != null && location.getExternalId() != null) {
            return location.getSource() + ":" + location.getExternalId();
        }

        return "id:" + location.getId();
    }

    private String buildOneMapExternalId(String name, String postalCode, double latitude, double longitude) {
        return cleanForKey(name) + "|"
                + cleanForKey(postalCode) + "|"
                + latitude + "|"
                + longitude;
    }

    private String buildCustomExternalId(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.6f|%.6f", latitude, longitude);
    }

    private List<String> buildLocationSearchQueries(String query) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);

        String simplified = query
                .replaceAll("(?i)\\b(mrt|station|bus|stop|interchange)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!simplified.isBlank()) {
            queries.add(simplified);
        }

        String[] tokens = simplified.split("\\s+");
        if (tokens.length > 1 && !tokens[0].isBlank()) {
            queries.add(tokens[0]);
        }

        return queries.stream().toList();
    }

    private int relevanceScore(LocationResponseDTO location, String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedName = cleanForSearch(location.getName());
        String searchableText = String.join(" ",
                normalizedName,
                cleanForSearch(location.getAddress()),
                cleanForSearch(location.getPostalCode())
        );

        int score = searchableText.contains(normalizedQuery) ? 100 : 0;
        if (normalizedName.equals(normalizedQuery)) {
            score += 1000;
        } else if (normalizedName.startsWith(normalizedQuery)) {
            score += 250;
        }
        if (LocationSource.CUSTOM.name().equals(location.getSource())) {
            score += 25;
        }
        for (String token : normalizedQuery.split("\\s+")) {
            if (!token.isBlank() && searchableText.contains(token)) {
                score += 10;
            }
        }

        return score;
    }

    private String cleanForSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String cleanForKey(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? "" : cleaned.toLowerCase();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            String cleaned = cleanNil(value);
            if (cleaned != null) return cleaned;
        }

        return null;
    }

    private String cleanRequired(String value, String message) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(message);
        }

        return cleaned;
    }

    private String cleanOptional(String value) {
        if (value == null) return null;

        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanNil(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null || "NIL".equalsIgnoreCase(cleaned)) return null;

        return cleaned;
    }

    private double requireCoordinate(Double value, String message) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private Double parseCoordinate(String value) {
        try {
            return value == null ? null : Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void validateSingaporeBounds(double latitude, double longitude) {
        if (!isWithinSingapore(latitude, longitude)) {
            throw new IllegalArgumentException("Location must be within Singapore bounds.");
        }
    }

    private boolean isWithinSingapore(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }
}

package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.location.LocationRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.location.LocationResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapLocationRequestDTO;
import com.siyu.fleet_mgmt_sys.service.location.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    @GetMapping("/search")
    public ResponseEntity<List<LocationResponseDTO>> searchLocations(@RequestParam String query) {
        return ResponseEntity.ok(locationService.searchLocations(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponseDTO> getLocation(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocation(id));
    }

    @PostMapping
    public ResponseEntity<LocationResponseDTO> createCustomLocation(@RequestBody LocationRequestDTO request) {
        LocationResponseDTO result = locationService.createCustomLocation(request);
        return ResponseEntity.created(URI.create("/locations/" + result.getId()))
                .body(result);
    }

    @PostMapping("/onemap")
    public ResponseEntity<LocationResponseDTO> saveSelectedOneMapLocation(
            @RequestBody OneMapLocationRequestDTO request
    ) {
        LocationResponseDTO result = locationService.saveSelectedOneMapLocation(request);
        return ResponseEntity.created(URI.create("/locations/" + result.getId()))
                .body(result);
    }
}

package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/route")
@RequiredArgsConstructor
public class RouteController {

    private final OneMapService routeService;

    @GetMapping("/coords") // coords are lat,lng format
    // sample req: /route/coords?start=1.3081,103.8551&end=1.2739,103.8012

    public ResponseEntity<OneMapRouteResponseDTO> getRoute(
            @RequestParam String start,
            @RequestParam String end)
    {
        return ResponseEntity.ok(routeService.getRoute(start,end));
    }
}

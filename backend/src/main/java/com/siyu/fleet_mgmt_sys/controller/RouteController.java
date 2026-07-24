package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.RouteResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.ColoredSegmentDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.service.route.RouteService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/onemap/coords") // coords are lat,lng format
    // sample req: GET
    // localhost:8080/route/onemap/coords?start=1.3081,103.8551&end=1.2739,103.8012
    public ResponseEntity<OneMapRouteResponseDTO> getOneMapRoute(
            @RequestParam String start,
            @RequestParam String end) {
        return ResponseEntity.ok(routeService.getOneMapRoute(start, end));
    }

    // sample req: GET
    // localhost:8080/route/coords?start=1.3081,103.8551&end=1.2739,103.8012&robotType=standard
    // sample req: GET
    // localhost:8080/route/coords?start=1.3081,103.8551&end=1.2739,103.8012&robotType=standard
    @GetMapping("/coords")
    public ResponseEntity<RouteResponseDTO> getRoute(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String robotType ) {
        RobotType typeEnum = RobotType.valueOf(robotType.toUpperCase());

        WayPoint startWp = new WayPoint(start);
        WayPoint endWp = new WayPoint(end);

        return ResponseEntity.ok(routeService.getRouteDTO(startWp, endWp, typeEnum));
    }


    @GetMapping("/colored-coords")
    public ResponseEntity<List<ColoredSegmentDTO>> getColoredRoute(
            @RequestParam String start,
            @RequestParam String end) {
        return ResponseEntity.ok(routeService.getColoredRoute(start, end));
    }
}

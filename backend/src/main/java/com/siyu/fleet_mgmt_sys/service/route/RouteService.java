package com.siyu.fleet_mgmt_sys.service.route;

// TODO: Integrate LTA API

// At the moment, Route Service simply converts the OneMapResponseDTO into a normal Route object
// In the future, 
import com.siyu.fleet_mgmt_sys.dto.RouteResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.ColoredSegmentDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RouteRepository;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {
    private final OneMapService oneMapService;

    private final RouteBuilderService routeBuilderService;

    private final RouteRepository routeRepository;

    // ─── Primary routing (graph-based) ───────────────────────────────────────

    /**
     * Primary route method - uses the graph-based A* router.
     * Returns a fully populated Route with polyline, distance, time,
     * and estimated times per robot type.
     *
     * @param start start waypoint
     * @param end   end waypoint
     * @return populated Route entity (not yet persisted)
     */

    public Route getRoute(WayPoint start, WayPoint end) {
//        log.info("Routing from ({},{}) to ({},{})",
//                start.getLatitude(), start.getLongitude(),
//                end.getLatitude(), end.getLongitude());

        return routeBuilderService.buildRoute(
                start.getLatitude(), start.getLongitude(),
                end.getLatitude(), end.getLongitude());
    }


    public RouteResponseDTO getRouteDTO(WayPoint start, WayPoint end, RobotType robotType) {
        Route route = getRoute(start, end);
        Route savedRoute = routeRepository.save(route);
        return RouteResponseDTO.builder()
                .estimatedTime(savedRoute.getEstimatedTimeFor(robotType))
                .id(savedRoute.getId())
                .routeGeo(savedRoute.getRouteGeo())
                .taskId(savedRoute.getSafeTaskId())
                .totalDistance(savedRoute.getTotalDistance()).build();

    }

    // ─── OneMap routing (fallback / controller use) ───────────────────────────

    /**
     * Returns a raw OneMap route response.
     * Used by the controller for direct OneMap routing,
     * or as a fallback if the graph router is unavailable.
     */
    public OneMapRouteResponseDTO getOneMapRoute(String start, String end) {
        return oneMapService.getRoute(start, end);
    }

    public Route getRouteForTask(Task task) {
        return task.getRoute() == null ? getRoute(task.getStartWayPoint(), task.getEndWayPoint()) : task.getRoute();
    }

    // Colors a route by the graph's own speed bands (kept in sync from LTA on a schedule, and
    // obstruction-aware) instead of hitting OneMap + LTA live on every request.
    public List<ColoredSegmentDTO> getColoredRoute(String start, String end) {
        WayPoint startWp = new WayPoint(start);
        WayPoint endWp = new WayPoint(end);
        return routeBuilderService.buildColoredRoute(
                startWp.getLatitude(), startWp.getLongitude(),
                endWp.getLatitude(), endWp.getLongitude());
    }

}

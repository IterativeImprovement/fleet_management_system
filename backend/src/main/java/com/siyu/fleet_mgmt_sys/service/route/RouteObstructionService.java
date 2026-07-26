package com.siyu.fleet_mgmt_sys.service.route;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.RouteRepository;
import com.siyu.fleet_mgmt_sys.service.WebsocketPublisherService;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles route obstruction events.
 *
 * When a road is obstructed:
 * 1. Mark all sub-edges of that road as obstructed in the in-memory graph
 * 2. Find all active robots whose current route passes through that road
 * 3. Reroute each affected robot from their current position
 * 4. Persist the new route and notify frontend via WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteObstructionService {

    private final RouteGraphService routeGraphService;
    private final RouteBuilderService routeBuilderService;
    private final RouteRepository routeRepository;
    private final RobotRepository robotRepository;
    private final WebsocketPublisherService websocketPublisherService;

    /**
     * Called when a ROUTE_OBSTRUCTION simulation event fires.
     * Obstructs the road in-memory and reroutes all affected active robots.
     *
     * @param linkId the linkId of the obstructed road
     */
    @Transactional
    public void handleObstruction(String linkId) {
        log.warn("OBSTRUCTION EVENT: linkId={}", linkId);

        // Step 1: Mark all sub-edges of this road as obstructed in the in-memory graph
        routeGraphService.obstructLink(linkId);

        // Step 2: Find all active robots whose current route contains this linkId
        List<Robot> affectedRobots = findAffectedRobots(linkId);

        if (affectedRobots.isEmpty()) {
            log.info("No active robots affected by obstruction on linkId={}", linkId);
            return;
        }

        log.warn("Obstruction on linkId={} affects {} robot(s)", linkId, affectedRobots.size());

        // Step 3: Reroute each affected robot
        for (Robot robot : affectedRobots) {
            rerouteRobot(robot, linkId);
        }
    }

    /**
     * Called when a road obstruction is cleared.
     * Restores the road's speed band in-memory.
     * Does not automatically reroute — robots will naturally use the road
     * again on their next routing calculation.
     *
     * @param linkId           the linkId of the cleared road
     * @param restoredSpeedBand the speed band to restore
     */
    public void handleObstructionCleared(String linkId, int restoredSpeedBand) {
        routeGraphService.clearObstruction(linkId, restoredSpeedBand);
        websocketPublisherService.publishObstructionCleared(linkId);
        log.info("Obstruction cleared on linkId={}, restored to band={}", linkId, restoredSpeedBand);
    }

    // ─── Finding affected robots ──────────────────────────────────────────────

    /**
     * Finds all active robots whose current route passes through the given linkId.
     * Only considers robots that are currently MOVING — idle or charging robots
     * do not have an active route to reroute.
     */
    private List<Robot> findAffectedRobots(String linkId) {
        return robotRepository.findAll().stream()
                .filter(this::isActivelyMoving)
                .filter(robot -> routeContainsLink(robot, linkId))
                .toList();
    }

    private boolean isActivelyMoving(Robot robot) {
        return robot.getStatus() == RobotStatus.MOVING_TO_BASE
                || robot.getStatus() == RobotStatus.ASSIGNED;
    }

    private boolean routeContainsLink(Robot robot, String linkId) {
        if (robot.getCurrentTask() == null) return false;
        if (robot.getCurrentTask().getRoute() == null) return false;
        List<String> linkIds = robot.getCurrentTask().getRoute().getLinkIds();
        if (linkIds == null || linkIds.isEmpty()) return false;
        return linkIds.contains(linkId);
    }

    // ─── Rerouting ────────────────────────────────────────────────────────────

    /**
     * Reroutes a single robot around the obstruction.
     * Uses the robot's current position as the new start point.
     * Persists the new route and notifies the frontend.
     */
    private void rerouteRobot(Robot robot, String obstructedLinkId) {
        log.warn("Rerouting robot {} (status={}) around obstruction on linkId={}",
                robot.getName(), robot.getStatus(), obstructedLinkId);

        try {
            // Current position — where the robot is right now
            double currentLat = robot.getLatitude();
            double currentLon = robot.getLongitude();

            // Destination is the end waypoint of the robot's current task
            WayPoint destination = robot.getCurrentTask().getEndWayPoint();

            // Build new route from current position to destination
            // A* will naturally avoid the obstructed road (speedBand=0 → skipped)
            Route newRoute = routeBuilderService.buildRoute(
                    currentLat, currentLon,
                    destination.getLatitude(), destination.getLongitude());

            // Persist new route
            routeRepository.save(newRoute);

            // Replace robot's task route
            robot.getCurrentTask().setRoute(newRoute);
            robotRepository.save(robot);

            log.info("Robot {} successfully rerouted: {}m, {} link(s)",
                    robot.getName(),
                    newRoute.getTotalDistance(),
                    newRoute.getLinkIds().size());

            // Notify frontend of new route
            websocketPublisherService.publishReroute(robot.getId(), newRoute);

        } catch (Exception e) {
            log.error("Failed to reroute robot {}: {}", robot.getName(), e.getMessage(), e);
            websocketPublisherService.publishRerouteFailed(robot.getId(), obstructedLinkId);
        }
    }
}
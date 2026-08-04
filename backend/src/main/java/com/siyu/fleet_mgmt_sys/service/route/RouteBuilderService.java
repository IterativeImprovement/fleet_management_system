package com.siyu.fleet_mgmt_sys.service.route;

import com.siyu.fleet_mgmt_sys.dto.external.ColoredSegmentDTO;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import com.siyu.fleet_mgmt_sys.model.robot.RobotAttributes;
import com.siyu.fleet_mgmt_sys.service.graph.LocalGraphView;
import com.siyu.fleet_mgmt_sys.service.graph.GraphBuilderService;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import com.siyu.fleet_mgmt_sys.util.PolylineEncoder;
import com.siyu.fleet_mgmt_sys.util.SpeedBandUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Route building service: Constructs a route based on start and end points.
 * Uses LTA Speed bands API to construct weighted graph, then uses A* to find the most optimal route.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteBuilderService {

    private final RouteGraphService routeGraphService;
    private final RouteOptimisationService routeOptimisationService;
    private final PolylineEncoder polylineEncoder;

    /**
     * Builds a Route from start to end coordinates.
     *
     * @param startLat start latitude
     * @param startLon start longitude
     * @param endLat   end latitude
     * @param endLon   end longitude
     * @return populated Route entity (not yet persisted)
     */
    public Route  buildRoute(double startLat, double startLon,
                            double endLat, double endLon) {

        log.info("Building route from ({},{}) to ({},{})",
                startLat, startLon, endLat, endLon);

        // Step 1: Create a fresh query-local graph view
        // Completely isolated - no other query can see temp edges added here
        LocalGraphView graphView = new LocalGraphView(routeGraphService);

        // Step 2: Project start and end coordinates onto nearest edges
        ProjectionResult startProjection = projectOntoNearestEdge(startLat, startLon);
        ProjectionResult endProjection   = projectOntoNearestEdge(endLat, endLon);

        // Step 3: Create temporary nodes at projection points. These nodes are unique for this query.
        /* Example: You want to travel from NUS to Harbourfront MRT station.
        There is no road that perfectly encompasses those locations, so the routing service picks projection points as
        "pick up" and "drop off" points. */

        // Negative IDs ensure they never collide with real persisted node IDs
        GraphNode startNode = createTempNode(-1L, startProjection.lat(), startProjection.lon());
        GraphNode endNode   = createTempNode(-2L, endProjection.lat(), endProjection.lon());

        // Step 4: Inject temp edges into the LOCAL view only.
        injectTempNode(startNode, startProjection.edge(), graphView);
        injectTempNode(endNode,   endProjection.edge(),   graphView);

        // Step 5: Run A* with the query-local view
        List<GraphEdge> path = routeOptimisationService.findFastestRoute(
                startNode, endNode, graphView);

        // Step 6: Encode polyline
        String polyline = polylineEncoder.encode(path);

        double totalDistanceMetres = path.stream()
                .mapToDouble(GraphEdge::getLengthMetres)
                .sum();

        Map<RobotType, Double> estimatedTimes = computeEstimatedTimesPerRobotType(path);

        // Step 7: Build and return Route entity
        Route route = new Route();
        route.setRouteGeo(polyline);
        route.setTotalDistance((int) Math.round(totalDistanceMetres));
        route.setEstimatedTimes(estimatedTimes);

        List<String> linkIds = path.stream()
                .map(GraphEdge::getLinkId)
                .distinct()
                .toList();

        route.setLinkIds(linkIds);

        StringBuilder logMessage = new StringBuilder("Route built:\n");

        estimatedTimes.forEach((type, time) ->
                logMessage.append(String.format("%s: %dm, %.1fs\n",
                        type.name(),
                        route.getTotalDistance(),
                        time))
        );

        log.info(logMessage.toString());

        return route;
    }

    /**
     * Updated: All coloured route needs from this frontend goes through this function instead of the LTA one,
     * bypassing the need to call external API entirely.
     */
    public List<ColoredSegmentDTO> buildColoredRoute(double startLat, double startLon,
                                                       double endLat, double endLon) {
        LocalGraphView graphView = new LocalGraphView(routeGraphService);

        ProjectionResult startProjection = projectOntoNearestEdge(startLat, startLon);
        ProjectionResult endProjection = projectOntoNearestEdge(endLat, endLon);

        GraphNode startNode = createTempNode(-1L, startProjection.lat(), startProjection.lon());
        GraphNode endNode = createTempNode(-2L, endProjection.lat(), endProjection.lon());

        injectTempNode(startNode, startProjection.edge(), graphView);
        injectTempNode(endNode, endProjection.edge(), graphView);

        List<GraphEdge> path = routeOptimisationService.findFastestRoute(startNode, endNode, graphView);

        List<ColoredSegmentDTO> segments = new ArrayList<>();
        for (GraphEdge edge : path) {
            int band = edge.getCurrentSpeedBand();
            List<List<Double>> coords = List.of(
                    List.of(edge.getFromNode().getLatitude(), edge.getFromNode().getLongitude()),
                    List.of(edge.getToNode().getLatitude(), edge.getToNode().getLongitude()));
            segments.add(new ColoredSegmentDTO(coords, bandToColor(band), band));
        }
        return segments;
    }


    private String bandToColor(int speedBand) {
        if (speedBand <= 3) return "#22c55e";
        if (speedBand <= 5) return "#f97316";
        return "#ef4444";
    }


    /**
     * Projects a lat/lng point onto the nearest edge in the graph.
     * Returns the projected point, the edge it lies on, and the distance to it.
     */
    private ProjectionResult projectOntoNearestEdge(double lat, double lon) {
        GraphEdge nearestEdge = null;
        double[] nearestPoint = null;
        double minDist = Double.MAX_VALUE;

        GraphEdge nearestUsableEdge = null;
        double[] nearestUsablePoint = null;
        double minUsableDist = Double.MAX_VALUE;

        for (GraphEdge edge : routeGraphService.getAllEdges()) {
            double[] projected = projectPointOntoSegment(
                    lat, lon,
                    edge.getFromNode().getLatitude(), edge.getFromNode().getLongitude(),
                    edge.getToNode().getLatitude(),   edge.getToNode().getLongitude());

            double dist = GraphBuilderService.haversineMetres(
                    lat, lon, projected[0], projected[1]);

            if (dist < minDist) {
                minDist = dist;
                nearestEdge = edge;
                nearestPoint = projected;
            }

            boolean usable = edge.getCurrentSpeedBand() != null && edge.getCurrentSpeedBand() > 0;
            if (usable && dist < minUsableDist) {
                minUsableDist = dist;
                nearestUsableEdge = edge;
                nearestUsablePoint = projected;
            }
        }

        if (nearestEdge == null) {
            throw new IllegalStateException(
                    "No edges in graph: cannot project point (" + lat + "," + lon + ")");
        }


        // Chooses nearest nonobstructed point if possible
        if (nearestUsableEdge != null) {
            return new ProjectionResult(nearestUsablePoint[0], nearestUsablePoint[1], nearestUsableEdge, minUsableDist);
        }

        // Fallback to nearest edge if all edges are obstructed
        return new ProjectionResult(nearestPoint[0], nearestPoint[1], nearestEdge, minDist);
    }

    /**
     * Projects point P onto segment AB, clamped to segment bounds.
     * Returns the closest point on the segment to P as [lat, lon].
     */
    private double[] projectPointOntoSegment(double pLat, double pLon,
                                             double aLat, double aLon,
                                             double bLat, double bLon) {
        double abLat = bLat - aLat;
        double abLon = bLon - aLon;
        double apLat = pLat - aLat;
        double apLon = pLon - aLon;

        double abLenSq = abLat * abLat + abLon * abLon;
        if (abLenSq == 0) return new double[]{ aLat, aLon }; // degenerate segment

        double t = (apLat * abLat + apLon * abLon) / abLenSq;
        t = Math.max(0, Math.min(1, t)); // clamp to [0, 1]

        return new double[]{ aLat + t * abLat, aLon + t * abLon };
    }

    /**
     * Creates a temporary in-memory node at the given coordinates.
     * Negative IDs never collide with persisted node IDs.
     */
    private GraphNode createTempNode(Long tempId, double lat, double lon) {
        return GraphNode.builder()
                .id(tempId)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    /**
     * Splits an edge at a temporary node's position and registers
     * the resulting sub-edges into the query-local graph view.
     *
     * Original edge A-B becomes two sub-edges: A to tempNode, and
     * tempNode to B.
     *
     * The original edge A-B remains in the shared graph unchanged.
     */
    private void injectTempNode(GraphNode tempNode, GraphEdge edge,
                                LocalGraphView graphView) {
        GraphNode from = edge.getFromNode();
        GraphNode to   = edge.getToNode();

        double distToTemp   = GraphBuilderService.haversineMetres(
                from.getLatitude(), from.getLongitude(),
                tempNode.getLatitude(), tempNode.getLongitude());
        double distFromTemp = GraphBuilderService.haversineMetres(
                tempNode.getLatitude(), tempNode.getLongitude(),
                to.getLatitude(), to.getLongitude());

        int band = edge.getCurrentSpeedBand();
        double speedMs = SpeedBandUtils.toMetresPerSecond(band);

        // from current pos to tempnode
        GraphEdge toTemp = GraphEdge.builder()
                .id(-1L)
                .fromNode(from)
                .toNode(tempNode)
                .linkId(edge.getLinkId())
                .roadName(edge.getRoadName())
                .lengthMetres(distToTemp)
                .currentSpeedBand(band)
                .travelTimeSeconds(distToTemp / speedMs)
                .build();

        // from tempnode to new pos
        GraphEdge fromTemp = GraphEdge.builder()
                .id(-2L)
                .fromNode(tempNode)
                .toNode(to)
                .linkId(edge.getLinkId())
                .roadName(edge.getRoadName())
                .lengthMetres(distFromTemp)
                .currentSpeedBand(band)
                .travelTimeSeconds(distFromTemp / speedMs)
                .build();

        // adds tempnode to local view only to avoid troubles with shared graph
        graphView.addTempEdge(from.getId(), toTemp);
        graphView.addTempEdge(tempNode.getId(), fromTemp);
    }

    /**
     * Computes total travel time for the given robot along the path.
     */

    private Map<RobotType, Double> computeEstimatedTimesPerRobotType(List<GraphEdge> path) {
        Map<RobotType, Double> times = new EnumMap<>(RobotType.class);

        for (RobotType type : RobotType.values()) {
            if (type == RobotType.UNINITIALISED) continue;

            double robotSpeedMs = getRobotSpeedMs(type);

            double totalTime = path.stream().mapToDouble(edge -> {
                double roadSpeedMs = SpeedBandUtils.toMetresPerSecond(edge.getCurrentSpeedBand());

                double effectiveSpeedMs = Math.min(robotSpeedMs, roadSpeedMs);
                return edge.getLengthMetres() / effectiveSpeedMs;
            }).sum();

            times.put(type, totalTime);
        }

        return times;
    }
    private double getRobotSpeedMs(RobotType type) {
        return switch (type) {
            case STANDARD -> RobotAttributes.Standard.SPEED;
            case LARGE -> RobotAttributes.Large.SPEED;
            default -> SpeedBandUtils.toMetresPerSecond(4);
        };
    }

    private record ProjectionResult(double lat, double lon,
                                    GraphEdge edge,
                                    double distanceMetres) {}

}
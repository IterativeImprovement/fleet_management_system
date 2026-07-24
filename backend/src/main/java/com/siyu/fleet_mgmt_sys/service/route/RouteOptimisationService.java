package com.siyu.fleet_mgmt_sys.service.route;

import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RouteNotFoundException;
import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.service.graph.GraphBuilderService;
import com.siyu.fleet_mgmt_sys.service.graph.LocalGraphView;
import com.siyu.fleet_mgmt_sys.util.SpeedBandUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RouteOptimisationService {

    /**
     * Finds the fastest route between two nodes using A*.
     * Uses a query-local graph view to safely include temp projection nodes.
     *
     * @param start           starting node
     * @param goal            destination node
     * @param graphView       query-local graph view (includes temp edges)
     * @return ordered list of edges representing the fastest path
     */
    public List<GraphEdge> findFastestRoute(GraphNode start,
                                            GraphNode goal,
                                            LocalGraphView graphView) {
        PriorityQueue<RouteState> openSet =
                new PriorityQueue<>(Comparator.comparingDouble(s -> s.fScore));

        Map<Long, Double> gScore  = new HashMap<>();
        Map<Long, GraphEdge> cameFrom = new HashMap<>();

        gScore.put(start.getId(), 0.0);
        openSet.add(new RouteState(start, 0.0));

        while (!openSet.isEmpty()) {
            RouteState current = openSet.poll();
            GraphNode currentNode = current.node;

            if (currentNode.getId().equals(goal.getId())) {
                List<GraphEdge> path = reconstructPath(cameFrom, goal.getId());
                log.debug("Route found: {} edges from node {} to node {}",
                        path.size(), start.getId(), goal.getId());
                return path;
            }

            double currentG = gScore.getOrDefault(currentNode.getId(), Double.MAX_VALUE);

            double robotMaxSpeedMs = Robot.getFastestSpeed();

            // Query-local view - sees shared + temp edges for this query only
            for (GraphEdge edge : graphView.getEdgesFrom(currentNode.getId())) {
                GraphNode neighbor = edge.getToNode();

                double roadSpeedMs    = SpeedBandUtils.toMetresPerSecond(edge.getCurrentSpeedBand());
                double effectiveSpeedMs = Math.min(roadSpeedMs, robotMaxSpeedMs);

                if (effectiveSpeedMs <= 0) continue;

                double traversalTime = (robotMaxSpeedMs >= roadSpeedMs)
                        ? edge.getTravelTimeSeconds()
                        : edge.getLengthMetres() / robotMaxSpeedMs;

                double tentativeG = currentG + traversalTime;

                if (tentativeG < gScore.getOrDefault(neighbor.getId(), Double.MAX_VALUE)) {
                    cameFrom.put(neighbor.getId(), edge);
                    gScore.put(neighbor.getId(), tentativeG);

                    double straightLine = GraphBuilderService.haversineMetres(
                            neighbor.getLatitude(), neighbor.getLongitude(),
                            goal.getLatitude(), goal.getLongitude());

                    double maxPossibleSpeedMs = Math.min(
                            SpeedBandUtils.toMetresPerSecond(8), robotMaxSpeedMs);
                    double heuristic = straightLine / maxPossibleSpeedMs;

                    openSet.add(new RouteState(neighbor, tentativeG + heuristic));
                }
            }
        }

        throw new RouteNotFoundException(start.getId(),goal.getId());
    }

    private List<GraphEdge> reconstructPath(Map<Long, GraphEdge> cameFrom, Long goalId) {
        List<GraphEdge> path = new ArrayList<>();
        Long currentId = goalId;
        while (cameFrom.containsKey(currentId)) {
            GraphEdge edge = cameFrom.get(currentId);
            path.add(edge);
            currentId = edge.getFromNode().getId();
        }
        Collections.reverse(path);
        return path;
    }

    private record RouteState(GraphNode node, double fScore) {}
}
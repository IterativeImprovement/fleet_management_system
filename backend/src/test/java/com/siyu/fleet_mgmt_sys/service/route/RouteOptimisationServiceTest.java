package com.siyu.fleet_mgmt_sys.service.route;

import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RouteNotFoundException;
import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import com.siyu.fleet_mgmt_sys.service.graph.LocalGraphView;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteOptimisationServiceTest {

    private final RouteOptimisationService service = new RouteOptimisationService();

    // All nodes sit at (0,0) so the haversine heuristic is 0 and A* reduces to
    // pure Dijkstra on travelTimeSeconds - fully deterministic, cost = the times we set.
    // The robot's max speed (~22.2 m/s) is >= every speed band, so A* always uses
    // each edge's travelTimeSeconds directly rather than re-deriving from length.

    @Test
    void findsTheShortestTimePath() {
        GraphNode n1 = node(1), n2 = node(2), n3 = node(3), n4 = node(4);
        List<GraphEdge> edges = List.of(
                edge(n1, n2, 5, 100),   // 1->2->4 : total 10s
                edge(n2, n4, 5, 100),
                edge(n1, n3, 8, 100),   // 1->3->4 : total 16s (slower)
                edge(n3, n4, 8, 100)
        );

        List<GraphEdge> path = service.findFastestRoute(n1, n4, viewOf(edges));

        assertEquals(List.of(1L, 2L, 4L), nodeIds(n1, path));
    }

    @Test
    void picksFasterRoadEvenWhenItIsLonger() {
        GraphNode n1 = node(1), slow = node(2), fast = node(3), n4 = node(4);
        // Via node 2: SHORT (20m total) but SLOW (200s total).
        // Via node 3: LONG (2000m total) but FAST (20s total).
        List<GraphEdge> edges = List.of(
                edge(n1, slow, 100, 10),
                edge(slow, n4, 100, 10),
                edge(n1, fast, 10, 1000),
                edge(fast, n4, 10, 1000)
        );

        List<GraphEdge> path = service.findFastestRoute(n1, n4, viewOf(edges));

        // It took the long-but-fast road: proves it optimises time, not distance.
        assertEquals(List.of(1L, 3L, 4L), nodeIds(n1, path));
        double chosenDistance = path.stream().mapToDouble(GraphEdge::getLengthMetres).sum();
        assertEquals(2000.0, chosenDistance);   // longer than the 20m alternative
    }

    @Test
    void throwsWhenNoPathExists() {
        GraphNode n1 = node(1), n2 = node(2), n4 = node(4);
        // 1->2 goes nowhere near the goal; node 4 is unreachable.
        List<GraphEdge> edges = List.of(edge(n1, n2, 5, 100));

        assertThrows(RouteNotFoundException.class,
                () -> service.findFastestRoute(n1, n4, viewOf(edges)));
    }



    private static GraphNode node(long id) {
        return GraphNode.builder().id(id).latitude(0.0).longitude(0.0).build();
    }

    private static GraphEdge edge(GraphNode from, GraphNode to, double timeSeconds, double lengthMetres) {
        return GraphEdge.builder()
                .fromNode(from)
                .toNode(to)
                .currentSpeedBand(8)   // any 1-8 keeps effectiveSpeed > 0
                .lengthMetres(lengthMetres)
                .travelTimeSeconds(timeSeconds)
                .build();
    }

    // Empty shared graph + our edges overlaid as temp edges for this query.
    private static LocalGraphView viewOf(List<GraphEdge> edges) {
        RouteGraphService sharedGraph = mock(RouteGraphService.class);
        when(sharedGraph.getEdgesFrom(anyLong())).thenReturn(List.of());
        LocalGraphView view = new LocalGraphView(sharedGraph);
        edges.forEach(e -> view.addTempEdge(e.getFromNode().getId(), e));
        return view;
    }

    private static List<Long> nodeIds(GraphNode start, List<GraphEdge> path) {
        List<Long> ids = new ArrayList<>();
        ids.add(start.getId());
        path.forEach(e -> ids.add(e.getToNode().getId()));
        return ids;
    }
}

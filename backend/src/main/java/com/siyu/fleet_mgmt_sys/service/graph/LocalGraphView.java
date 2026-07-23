package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A query-local view of the road graph.
 * Wraps the shared RouteGraphService and overlays temporary edges
 * for start/end projection nodes without mutating the shared graph.
 *
 * Each routing query gets its own LocalGraphView instance.
 * Temp edges are never visible to other queries.
 */
public class LocalGraphView {

    private final RouteGraphService sharedGraph;

    // Temp edges added only for this query — never touch the shared graph
    private final Map<Long, List<GraphEdge>> tempAdjacency = new HashMap<>();

    public LocalGraphView(RouteGraphService sharedGraph) {
        this.sharedGraph = sharedGraph;
    }

    /**
     * Returns outgoing edges for a node.
     * Merges shared graph edges with any temp edges for this query.
     */
    public List<GraphEdge> getEdgesFrom(Long nodeId) {
        List<GraphEdge> shared = sharedGraph.getEdgesFrom(nodeId);
        List<GraphEdge> temp   = tempAdjacency.get(nodeId);

        if (temp == null || temp.isEmpty()) return shared;

        // Merge — shared edges + temp edges for this node
        List<GraphEdge> merged = new ArrayList<>(shared);
        merged.addAll(temp);
        return merged;
    }

    /**
     * Adds a temporary edge into this query's local overlay only.
     * The shared graph is never touched.
     */
    public void addTempEdge(Long fromNodeId, GraphEdge edge) {
        tempAdjacency.computeIfAbsent(fromNodeId, k -> new ArrayList<>()).add(edge);
    }
}
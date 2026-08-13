package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-request graph overlay for temporary route projection edges. */
public class LocalGraphView {

    private final RouteGraphService sharedGraph;

    // Temp edges added only for this query - never touch the shared graph
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

        // Merge - shared edges + temp edges for this node
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
package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import com.siyu.fleet_mgmt_sys.repository.GraphEdgeRepository;
import com.siyu.fleet_mgmt_sys.repository.GraphNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the road graph in memory for fast Dijkstra queries.
 * Loaded once from DB on startup. Updated in-place when speed bands change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteGraphService {

    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final GraphBuilderService graphBuilderService;

    // nodeId → node
    @Getter
    private Map<Long, GraphNode> nodes = new ConcurrentHashMap<>();

    // nodeId → list of outgoing edges (For Dijkstra)
    @Getter
    private Map<Long, List<GraphEdge>> adjacency = new ConcurrentHashMap<>();

    // linkId → list of edges (For instant O(1) LTA updates)
    private Map<String, List<GraphEdge>> edgesByLinkId = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        graphBuilderService.buildIfEmpty();
        reload();
    }

    /**
     * Reloads the full graph from DB into memory.
     */
    public void reload() {
        log.info("Loading graph into memory...");

        List<GraphNode> allNodes = graphNodeRepository.findAll();
        List<GraphEdge> allEdges = graphEdgeRepository.findAllWithNodes();

        Map<Long, GraphNode> newNodes = new ConcurrentHashMap<>();
        for (GraphNode node : allNodes) {
            newNodes.put(node.getId(), node);
        }

        Map<Long, List<GraphEdge>> newAdjacency = new ConcurrentHashMap<>();
        Map<String, List<GraphEdge>> newEdgesByLinkId = new ConcurrentHashMap<>();

        for (GraphEdge edge : allEdges) {
            // Populate Adjacency List
            newAdjacency
                    .computeIfAbsent(edge.getFromNode().getId(), k -> new ArrayList<>())
                    .add(edge);

            // Populate LinkId Index
            if (edge.getLinkId() != null) {
                newEdgesByLinkId
                        .computeIfAbsent(edge.getLinkId(), k -> new ArrayList<>())
                        .add(edge);
            }
        }

        // Hot swap references safely
        this.nodes = newNodes;
        this.adjacency = newAdjacency;
        this.edgesByLinkId = newEdgesByLinkId;

        log.info("Graph loaded: {} nodes, {} edges", nodes.size(), allEdges.size());
    }

    /**
     * Used by the scheduled task to diff against current state.
     */
    public Integer getSpeedBand(String linkId) {
        List<GraphEdge> edges = edgesByLinkId.get(linkId);
        if (edges != null && !edges.isEmpty()) {
            return edges.get(0).getCurrentSpeedBand();
        }
        return null;
    }

    /**
     * Updates an edge's speed band and recalculates travel time.
     * Uses O(1) lookup via the edgesByLinkId index.
     */
    public void updateSpeedBand(String linkId, int newSpeedBand) {
        List<GraphEdge> edges = edgesByLinkId.get(linkId);
        if (edges == null) return;

        double speedMps = getSpeedInMetresPerSecond(newSpeedBand);

        for (GraphEdge edge : edges) {
            edge.setCurrentSpeedBand(newSpeedBand);
            // Must update the weight used by Dijkstra!
            edge.setTravelTimeSeconds(edge.getLengthMetres() / speedMps);
        }
    }

    // --- Graph Getters ---

    public List<GraphEdge> getEdgesFrom(Long nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    public GraphNode getNode(Long nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<GraphNode> getAllNodes() {
        return nodes.values();
    }

    public List<GraphEdge> getAllEdges() {
        return adjacency.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    // --- Utility ---

    private double getSpeedInMetresPerSecond(int speedBand) {
        return switch (speedBand) {
            case 1 -> 85.0 / 3.6;
            case 2 -> 75.0 / 3.6;
            case 3 -> 55.0 / 3.6;
            case 4 -> 35.0 / 3.6;
            case 5 -> 25.0 / 3.6;
            case 6 -> 15.0 / 3.6;
            case 7 -> 10.0 / 3.6;
            case 8 -> 5.0 / 3.6;
            default -> 40.0 / 3.6;
        };
    }
}
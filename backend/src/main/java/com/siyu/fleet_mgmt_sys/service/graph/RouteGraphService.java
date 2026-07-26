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
import com.siyu.fleet_mgmt_sys.util.SpeedBandUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the road graph in memory for fast A* queries.
 * Loaded once from DB on startup. Updated in-place when speed bands change.
 *
 * Concurrency model:
 * - adjacency and nodes use ConcurrentHashMap for safe concurrent reads
 * - adjacency lists use CopyOnWriteArrayList — lock-free reads, safe iteration
 * - speed band updates replace GraphEdge references atomically per list entry
 * - travelTimeSeconds is volatile on GraphEdge for safe cross-thread visibility
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteGraphService {

    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;
    private final GraphBuilderService graphBuilderService;

    @Getter
    private volatile Map<Long, GraphNode> nodes = new ConcurrentHashMap<>();

    @Getter
    private volatile Map<Long, CopyOnWriteArrayList<GraphEdge>> adjacency = new ConcurrentHashMap<>();

    private volatile Map<String, List<Long>> fromNodeIdsByLinkId = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        graphBuilderService.buildIfEmpty();
        reload();
    }

    /**
     * Reloads the full graph from DB into memory.
     * Builds new maps fully before hot-swapping - no partial state visible to readers.
     */
    public void reload() {
        log.info("Loading graph into memory...");

        List<GraphNode> allNodes = graphNodeRepository.findAll();
        List<GraphEdge> allEdges = graphEdgeRepository.findAllWithNodes();

        Map<Long, GraphNode> newNodes = new ConcurrentHashMap<>();
        for (GraphNode node : allNodes) {
            newNodes.put(node.getId(), node);
        }

        Map<Long, CopyOnWriteArrayList<GraphEdge>> newAdjacency = new ConcurrentHashMap<>();
        Map<String, List<Long>> newFromNodeIdsByLinkId = new ConcurrentHashMap<>();

        for (GraphEdge edge : allEdges) {
            newAdjacency
                    .computeIfAbsent(edge.getFromNode().getId(), k -> new CopyOnWriteArrayList<>())
                    .add(edge);

            if (edge.getLinkId() != null) {
                List<Long> fromNodeIds = newFromNodeIdsByLinkId
                        .computeIfAbsent(edge.getLinkId(), k -> new ArrayList<>());
                if (!fromNodeIds.contains(edge.getFromNode().getId())) {
                    fromNodeIds.add(edge.getFromNode().getId());
                }
            }
        }

        // Atomic hot swap — readers always see a fully built graph
        this.nodes = newNodes;
        this.adjacency = newAdjacency;
        this.fromNodeIdsByLinkId = newFromNodeIdsByLinkId;

        log.info("Graph loaded: {} nodes, {} edges", nodes.size(), allEdges.size());
    }

    /**
     * Updates speed band for all edges with the given linkId, by rewriting them in place inside
     * whichever `adjacency` lists actually hold them — see the FIX comment on fromNodeIdsByLinkId
     * for why this must mutate the SAME lists A* reads rather than a separate copy.
     */
    public void updateSpeedBand(String linkId, int newSpeedBand) {
        List<Long> fromNodeIds = fromNodeIdsByLinkId.get(linkId);
        if (fromNodeIds == null) return;

        double speedMs = SpeedBandUtils.toMetresPerSecond(newSpeedBand);

        for (Long fromNodeId : fromNodeIds) {
            CopyOnWriteArrayList<GraphEdge> edges = adjacency.get(fromNodeId);
            if (edges == null) continue;

            // Replace each edge with a fresh instance — atomic from reader's perspective
            // (roadName must be carried over — dropping it here reverts road names to "Unknown Road"
            // on every 5-minute traffic refresh, since GraphUpdateService calls this on a schedule).
            // This adjacency list may hold edges for OTHER roads too (several roads can share a
            // junction node), so only rewrite the ones matching this linkId.
            edges.replaceAll(edge -> {
                if (!linkId.equals(edge.getLinkId())) return edge;
                return GraphEdge.builder()
                        .id(edge.getId())
                        .fromNode(edge.getFromNode())
                        .toNode(edge.getToNode())
                        .linkId(edge.getLinkId())
                        .roadName(edge.getRoadName())
                        .lengthMetres(edge.getLengthMetres())
                        .currentSpeedBand(newSpeedBand)
                        .travelTimeSeconds(edge.getLengthMetres() / speedMs)
                        .build();
            });
        }
    }

    public Integer getSpeedBand(String linkId) {
        GraphEdge edge = firstEdgeForLinkId(linkId);
        return edge != null ? edge.getCurrentSpeedBand() : null;
    }

    public List<GraphEdge> getEdgesFrom(Long nodeId) {
        CopyOnWriteArrayList<GraphEdge> edges = adjacency.get(nodeId);
        return edges != null ? edges : List.of();
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

    /**
     * Returns every graph sub-edge sharing the given road linkId — a road that crossed one or more
     * other roads gets split into several sub-edges at those junctions (see
     * GraphBuilderService.splitEdge), so a single Road row's raw start/end line is not the whole
     * physical road. Used to draw the true, possibly-multi-segment geometry of an obstructed road
     * on the frontend instead of just a straight line between its two original endpoints.
     */
    public List<GraphEdge> getEdgesByLinkId(String linkId) {
        List<Long> fromNodeIds = fromNodeIdsByLinkId.get(linkId);
        if (fromNodeIds == null) return List.of();

        List<GraphEdge> result = new ArrayList<>();
        for (Long fromNodeId : fromNodeIds) {
            CopyOnWriteArrayList<GraphEdge> edges = adjacency.get(fromNodeId);
            if (edges == null) continue;
            for (GraphEdge edge : edges) {
                if (linkId.equals(edge.getLinkId())) result.add(edge);
            }
        }
        return result;
    }

    private GraphEdge firstEdgeForLinkId(String linkId) {
        List<Long> fromNodeIds = fromNodeIdsByLinkId.get(linkId);
        if (fromNodeIds == null) return null;
        for (Long fromNodeId : fromNodeIds) {
            CopyOnWriteArrayList<GraphEdge> edges = adjacency.get(fromNodeId);
            if (edges == null) continue;
            for (GraphEdge edge : edges) {
                if (linkId.equals(edge.getLinkId())) return edge;
            }
        }
        return null;
    }

    /**
     * Marks all edges with the given linkId as obstructed.
     * A* skips edges with speedBand 0 (effectiveSpeed <= 0 check).
     * Logs the obstruction for route audit trail.
     */
    public void obstructLink(String linkId) {
        List<Long> fromNodeIds = fromNodeIdsByLinkId.get(linkId);
        if (fromNodeIds == null) {
            log.warn("Obstruction event for unknown linkId: {}", linkId);
            return;
        }

        for (Long fromNodeId : fromNodeIds) {
            CopyOnWriteArrayList<GraphEdge> edges = adjacency.get(fromNodeId);
            if (edges == null) continue;
            edges.replaceAll(edge -> {
                if (!linkId.equals(edge.getLinkId())) return edge;
                return GraphEdge.builder()
                        .id(edge.getId())
                        .fromNode(edge.getFromNode())
                        .toNode(edge.getToNode())
                        .linkId(edge.getLinkId())
                        .roadName(edge.getRoadName())
                        .lengthMetres(edge.getLengthMetres())
                        .currentSpeedBand(0)       // 0 = obstructed — A* skips (effectiveSpeed <= 0)
                        .travelTimeSeconds(Double.MAX_VALUE)
                        .build();
            });
        }

        GraphEdge sample = firstEdgeForLinkId(linkId);
        log.warn("OBSTRUCTION: road {} ({}) marked as blocked",
                sample != null ? sample.getRoadName() : "unknown", linkId);
    }

    /**
     * Clears an obstruction — restores last known speed band from DB.
     */
    public void clearObstruction(String linkId, int restoredSpeedBand) {
        updateSpeedBand(linkId, restoredSpeedBand);
        GraphEdge sample = firstEdgeForLinkId(linkId);
        log.info("OBSTRUCTION CLEARED: road {} ({}) restored to band {}",
                sample != null ? sample.getRoadName() : "unknown",
                linkId, restoredSpeedBand);
    }
}
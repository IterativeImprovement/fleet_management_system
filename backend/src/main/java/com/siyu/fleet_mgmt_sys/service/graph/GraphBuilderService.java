package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.model.Road;
import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import com.siyu.fleet_mgmt_sys.repository.GraphEdgeRepository;
import com.siyu.fleet_mgmt_sys.repository.GraphNodeRepository;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
import com.siyu.fleet_mgmt_sys.util.SpeedBandUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.siyu.fleet_mgmt_sys.util.SpeedBandUtils.toMetresPerSecond;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Constructs a persistent graph based on road segment info from LTA API.
 *
 * Order of operations:
 * 1. Create nodes from all road endpoints
 * 2. Create initial forward edges between endpoint nodes, calculating baseline travel times
 * 3. Detect crossings, insert junction nodes, split edges at junctions
 */
public class GraphBuilderService {

    private static final double SNAP_THRESHOLD_DEGREES = 0.000001;  // filtering out floating point noise
    private static final double GRID_CELL_SIZE = 0.01;              // ~1km cells
    private static final int DEFAULT_SPEED_BAND = 4;                // ~35 km/h fallback

    private final RoadRepository roadRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;

    public void buildIfEmpty() {
        if (graphNodeRepository.count() > 0) {
            log.info("Graph already built, skipping.");
            return;
        }
        log.info("Building road graph...");
        build();
    }

    @Transactional
    public void build() {
        // Step 1: Fetch all roads with speed bands in one query (avoids N+1)
        List<Road> roads = roadRepository.findAllWithSpeedBands();
        log.info("Loaded {} road segments", roads.size());

        // Step 2: Create nodes from all road endpoints, filtering out floating point noise
        Map<String, GraphNode> nodesByKey = new LinkedHashMap<>();
        for (Road road : roads) {
            getOrCreateNode(road.getStartLat(), road.getStartLon(), nodesByKey);
            getOrCreateNode(road.getEndLat(), road.getEndLon(), nodesByKey);
        }

        // Save only the initial endpoint nodes
        graphNodeRepository.saveAll(new ArrayList<>(nodesByKey.values()));
        log.info("Saved {} graph nodes from road endpoints", nodesByKey.size());

        // Step 3: Create initial forward-only edges between endpoint nodes
        List<GraphEdge> initialEdges = new ArrayList<>();
        for (Road road : roads) {
            GraphNode from = nodesByKey.get(snapKey(road.getStartLat(), road.getStartLon()));
            GraphNode to   = nodesByKey.get(snapKey(road.getEndLat(), road.getEndLon()));

            if (from == null || to == null || from == to) continue;

            int speedBand = road.getRoadSpeedBand() != null
                    ? road.getRoadSpeedBand().getSpeedBand() != null
                      ? road.getRoadSpeedBand().getSpeedBand()
                      : DEFAULT_SPEED_BAND
                    : DEFAULT_SPEED_BAND;

            double length = haversineMetres(
                    road.getStartLat(), road.getStartLon(),
                    road.getEndLat(), road.getEndLon());

            double speedMps = SpeedBandUtils.toMetresPerSecond(speedBand);
            double travelTime = length / speedMps;

            initialEdges.add(GraphEdge.builder()
                    .fromNode(from)
                    .toNode(to)
                    .linkId(road.getId())
                    .roadName(road.getRoadName())   // ← fixed: was missing
                    .lengthMetres(length)
                    .currentSpeedBand(speedBand)
                    .travelTimeSeconds(travelTime)
                    .build());
        }
        log.info("Created {} initial forward edges", initialEdges.size());

        // Step 4: Detect crossings, insert junction nodes, split edges
        List<GraphEdge> finalEdges = resolveIntersections(initialEdges, nodesByKey);
        graphEdgeRepository.saveAll(finalEdges);
        log.info("Saved {} final directional edges", finalEdges.size());
    }

    // ─── Intersection resolution ──────────────────────────────────────────────

    private List<GraphEdge> resolveIntersections(List<GraphEdge> edges,
                                                 Map<String, GraphNode> nodesByKey) {
        // Build spatial grid from edges
        List<RoadSegment> segments = edgesToSegments(edges);
        Map<String, List<Integer>> grid = buildGrid(segments);

        // Collect junction points per edge index
        Map<Integer, List<double[]>> junctionPoints = new HashMap<>();
        Set<String> checked = new HashSet<>();

        for (List<Integer> cellIndices : grid.values()) {
            for (int i = 0; i < cellIndices.size(); i++) {
                for (int j = i + 1; j < cellIndices.size(); j++) {
                    int idxA = cellIndices.get(i);
                    int idxB = cellIndices.get(j);

                    String pairKey = idxA + ":" + idxB;
                    if (!checked.add(pairKey)) continue;

                    RoadSegment a = segments.get(idxA);
                    RoadSegment b = segments.get(idxB);

                    double[] intersection = intersect(
                            a.x1, a.y1, a.x2, a.y2,
                            b.x1, b.y1, b.x2, b.y2);

                    if (intersection != null) {
                        // Create junction node — only if not already exists
                        getOrCreateNode(intersection[0], intersection[1], nodesByKey);
                        junctionPoints
                                .computeIfAbsent(idxA, k -> new ArrayList<>())
                                .add(intersection);
                        junctionPoints
                                .computeIfAbsent(idxB, k -> new ArrayList<>())
                                .add(intersection);
                    }
                }
            }
        }

        // Save only newly created junction nodes (id == null means not yet persisted)
        List<GraphNode> newNodes = nodesByKey.values().stream()
                .filter(n -> n.getId() == null)
                .toList();
        if (!newNodes.isEmpty()) {
            graphNodeRepository.saveAll(newNodes);
            log.info("Inserted {} junction nodes", newNodes.size());
        }

        // Build final edge list — split where needed, respecting original direction
        List<GraphEdge> finalEdges = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            GraphEdge edge = edges.get(i);
            List<double[]> junctions = junctionPoints.get(i);

            if (junctions == null || junctions.isEmpty()) {
                // No junction on this edge — add original forward direction only
                finalEdges.add(edge);
            } else {
                // Has junctions — split produces sequential forward edges
                finalEdges.addAll(splitEdge(edge, junctions, nodesByKey));
            }
        }

        return finalEdges;
    }

    // ─── Spatial grid (index-based) ───────────────────────────────────────────

    private List<RoadSegment> edgesToSegments(List<GraphEdge> edges) {
        List<RoadSegment> segments = new ArrayList<>();
        for (GraphEdge edge : edges) {
            segments.add(new RoadSegment(
                    edge.getLinkId(),
                    edge.getFromNode().getLatitude(),
                    edge.getFromNode().getLongitude(),
                    edge.getToNode().getLatitude(),
                    edge.getToNode().getLongitude(),
                    edge.getCurrentSpeedBand(),
                    edge.getRoadName()
            ));
        }
        return segments;
    }

    private Map<String, List<Integer>> buildGrid(List<RoadSegment> segments) {
        Map<String, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            for (String cellKey : getCellKeys(segments.get(i))) {
                grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(i);
            }
        }
        return grid;
    }

    private List<String> getCellKeys(RoadSegment seg) {
        double minLat = Math.min(seg.x1, seg.x2);
        double maxLat = Math.max(seg.x1, seg.x2);
        double minLon = Math.min(seg.y1, seg.y2);
        double maxLon = Math.max(seg.y1, seg.y2);

        // Convert coordinates to exact integer indices to prevent floating point drift
        int minLatIdx = (int) Math.floor(minLat / GRID_CELL_SIZE);
        int maxLatIdx = (int) Math.floor(maxLat / GRID_CELL_SIZE);
        int minLonIdx = (int) Math.floor(minLon / GRID_CELL_SIZE);
        int maxLonIdx = (int) Math.floor(maxLon / GRID_CELL_SIZE);

        List<String> keys = new ArrayList<>();
        for (int latIdx = minLatIdx; latIdx <= maxLatIdx; latIdx++) {
            for (int lonIdx = minLonIdx; lonIdx <= maxLonIdx; lonIdx++) {
                keys.add(latIdx + ":" + lonIdx);
            }
        }
        return keys;
    }

    // ─── Intersection math ────────────────────────────────────────────────────

    private double[] intersect(double x1, double y1, double x2, double y2,
                               double x3, double y3, double x4, double y4) {
        double d1x = x2 - x1, d1y = y2 - y1;
        double d2x = x4 - x3, d2y = y4 - y3;

        double cross = d1x * d2y - d1y * d2x;
        if (Math.abs(cross) < 1e-10) return null;

        double t = ((x3 - x1) * d2y - (y3 - y1) * d2x) / cross;
        double u = ((x3 - x1) * d1y - (y3 - y1) * d1x) / cross;

        if (t > 1e-9 && t < 1 - 1e-9 && u > 1e-9 && u < 1 - 1e-9) {
            return new double[]{ x1 + t * d1x, y1 + t * d1y };
        }
        return null;
    }

    // ─── Edge splitting ───────────────────────────────────────────────────────

    private List<GraphEdge> splitEdge(GraphEdge edge, List<double[]> junctions,
                                      Map<String, GraphNode> nodesByKey) {
        double startLat = edge.getFromNode().getLatitude();
        double startLon = edge.getFromNode().getLongitude();

        // Sort junctions by distance from edge start
        junctions.sort(Comparator.comparingDouble(p ->
                Math.pow(p[0] - startLat, 2) + Math.pow(p[1] - startLon, 2)));

        List<GraphEdge> result = new ArrayList<>();
        GraphNode prev = edge.getFromNode();
        int subIndex = 1;  // ← counter for sub-edge naming, scoped to this split call

        int safeSpeedBand = (edge.getCurrentSpeedBand() != null && edge.getCurrentSpeedBand() > 0)
                ? edge.getCurrentSpeedBand()
                : DEFAULT_SPEED_BAND;
        double speedMps = toMetresPerSecond(safeSpeedBand);

        for (double[] junction : junctions) {
            GraphNode junctionNode = nodesByKey.get(snapKey(junction[0], junction[1]));
            if (junctionNode == null || junctionNode == prev) continue;

            double length = haversineMetres(
                    prev.getLatitude(), prev.getLongitude(),
                    junctionNode.getLatitude(), junctionNode.getLongitude());

            result.add(GraphEdge.builder()
                    .fromNode(prev)
                    .toNode(junctionNode)
                    .linkId(edge.getLinkId())
                    .roadName(edge.getRoadName() + "-" + subIndex++)  // ← named sequentially
                    .lengthMetres(length)
                    .currentSpeedBand(safeSpeedBand)
                    .travelTimeSeconds(length / speedMps)
                    .build());

            prev = junctionNode;
        }

        // Final sub-edge to original end node
        GraphNode end = edge.getToNode();
        double length = haversineMetres(
                prev.getLatitude(), prev.getLongitude(),
                end.getLatitude(), end.getLongitude());

        result.add(GraphEdge.builder()
                .fromNode(prev)
                .toNode(end)
                .linkId(edge.getLinkId())
                .roadName(edge.getRoadName() + "-" + subIndex)  // ← final segment named too
                .lengthMetres(length)
                .currentSpeedBand(safeSpeedBand)
                .travelTimeSeconds(length / speedMps)
                .build());

        return result;
    }

    // ─── Node helpers ─────────────────────────────────────────────────────────

    private GraphNode getOrCreateNode(double lat, double lon,
                                      Map<String, GraphNode> nodesByKey) {
        String key = snapKey(lat, lon);
        return nodesByKey.computeIfAbsent(key, k ->
                GraphNode.builder().latitude(lat).longitude(lon).build());
    }

    private String snapKey(double lat, double lon) {
        long latKey = Math.round(lat / SNAP_THRESHOLD_DEGREES);
        long lonKey = Math.round(lon / SNAP_THRESHOLD_DEGREES);
        return latKey + ":" + lonKey;
    }

    // ─── Haversine ────────────────────────────────────────────────────────────

    public static double haversineMetres(double lat1, double lon1,
                                         double lat2, double lon2) {
        final double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Internal record ──────────────────────────────────────────────────────

    private record RoadSegment(String linkId,
                               double x1, double y1,
                               double x2, double y2,
                               int speedBand,
                               String roadName) {}
}
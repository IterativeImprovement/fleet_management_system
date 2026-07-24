package com.siyu.fleet_mgmt_sys.util;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.model.graph.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolylineRoundTripTest {

    // Standard Google polyline example takenfrom the algorithm's reference docs
    private static final double[][] GOOGLE_POINTS = {
            { 38.5, -120.2 },
            { 40.7, -120.95 },
            { 43.252, -126.453 },
    };
    private static final String GOOGLE_ENCODED = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    // Encoding rounds lat/lng to 1e-5, so that is the tightest achievable
    // tolerance.
    private static final double TOLERANCE = 1e-5;

    @Test
    void decodesKnownGoogleVector() {
        List<double[]> points = PolylineDecoder.decode(GOOGLE_ENCODED);

        assertEquals(GOOGLE_POINTS.length, points.size());
        for (int i = 0; i < GOOGLE_POINTS.length; i++) {
            assertEquals(GOOGLE_POINTS[i][0], points.get(i)[0], TOLERANCE);
            assertEquals(GOOGLE_POINTS[i][1], points.get(i)[1], TOLERANCE);
        }
    }

    @Test
    void roundTripsCoordsThroughEncodeDecode() {
        String encoded = new PolylineEncoder().encode(edgesFrom(GOOGLE_POINTS));
        List<double[]> decoded = PolylineDecoder.decode(encoded);

        assertEquals(GOOGLE_POINTS.length, decoded.size());
        for (int i = 0; i < GOOGLE_POINTS.length; i++) {
            assertEquals(GOOGLE_POINTS[i][0], decoded.get(i)[0], TOLERANCE);
            assertEquals(GOOGLE_POINTS[i][1], decoded.get(i)[1], TOLERANCE);
        }
    }

    // Encoder emits fromNode of edge 0 then toNode of every edge, so N points ->
    // N-1 chained edges.
    private static List<GraphEdge> edgesFrom(double[][] coords) {
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < coords.length - 1; i++) {
            edges.add(GraphEdge.builder()
                    .fromNode(node(coords[i]))
                    .toNode(node(coords[i + 1]))
                    .build());
        }
        return edges;
    }

    private static GraphNode node(double[] coord) {
        return GraphNode.builder().latitude(coord[0]).longitude(coord[1]).build();
    }
}

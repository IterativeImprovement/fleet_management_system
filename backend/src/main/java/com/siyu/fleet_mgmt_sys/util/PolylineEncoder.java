package com.siyu.fleet_mgmt_sys.util;

import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Encodes a list of graph edges into a Google Encoded Polyline string.
 * Also logs every road name traversed for route auditing (logs currently commented out for simplicity).
 *
 * Google Encoded Polyline format:
 * - Each lat/lng is multiplied by 1e5, rounded, differenced from previous, then encoded
 * - Each value is split into 5-bit chunks, reversed, OR'd with 0x20 if more chunks follow,
 *   then offset by 63 and appended as ASCII characters
 */
@Slf4j
@Service
public class PolylineEncoder {

    /**
     * Encodes the route into a Google Encoded Polyline string.
     * Logs every road segment traversed in order.
     *
     * @param edges ordered list of edges from A* result
     * @return encoded polyline string
     */
    public String encode(List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            log.warn("No edges to encode");
            return "";
        }

        StringBuilder encoded = new StringBuilder();
        int prevLat = 0;
        int prevLon = 0;

        // log.info("Route traversal ({} segments):", edges.size());

        String prevRoadName = null;
        int segmentNumber = 1;

        for (int i = 0; i < edges.size(); i++) {
            GraphEdge edge = edges.get(i);

            // Log each road name - group consecutive edges on same road
            String roadName = edge.getRoadName() != null ? edge.getRoadName() : "Unknown Road";
            if (!roadName.equals(prevRoadName)) {
//                log.info("  [{}] {}", segmentNumber++, roadName);
                prevRoadName = roadName;
            }

            // Encode start node of first edge only, then end nodes of all edges
            // This avoids duplicating junction coordinates
            if (i == 0) {
                double startLat = edge.getFromNode().getLatitude();
                double startLon = edge.getFromNode().getLongitude();
                int encodedLat = (int) Math.round(startLat * 1e5);
                int encodedLon = (int) Math.round(startLon * 1e5);
                encoded.append(encodeValue(encodedLat - prevLat));
                encoded.append(encodeValue(encodedLon - prevLon));
                prevLat = encodedLat;
                prevLon = encodedLon;
            }

            // Always encode end node
            double endLat = edge.getToNode().getLatitude();
            double endLon = edge.getToNode().getLongitude();
            int encodedLat = (int) Math.round(endLat * 1e5);
            int encodedLon = (int) Math.round(endLon * 1e5);
            encoded.append(encodeValue(encodedLat - prevLat));
            encoded.append(encodeValue(encodedLon - prevLon));
            prevLat = encodedLat;
            prevLon = encodedLon;
        }

        // log.info("Encoded polyline length: {} characters", encoded.length());
        return encoded.toString();
    }

    /**
     * Encodes a single integer value using the Google Polyline algorithm.
     */
    private String encodeValue(int value) {
        // Left-shift and invert if negative
        int shifted = value < 0 ? ~(value << 1) : (value << 1);

        StringBuilder result = new StringBuilder();
        while (shifted >= 0x20) {
            result.append((char) ((0x20 | (shifted & 0x1F)) + 63));
            shifted >>= 5;
        }
        result.append((char) (shifted + 63));
        return result.toString();
    }
}
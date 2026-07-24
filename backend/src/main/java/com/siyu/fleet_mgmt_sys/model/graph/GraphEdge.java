package com.siyu.fleet_mgmt_sys.model.graph;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graph_edges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * This represents a section of uninterrupted road (i.e. no junctions)
 * Used in routing algorithm.
 * All roads are assumed to be one way in the direction specified.
 */
public class GraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roadName;  // e.g. "NARAYANAN CHETTY ROAD" or "NARAYANAN CHETTY ROAD-1"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id")
    private GraphNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id")
    private GraphNode toNode;

    private String linkId;
    private Double lengthMetres;
    private Integer currentSpeedBand;

    @Transient  // computed in memory, not stored in DB
    private double travelTimeSeconds;
}
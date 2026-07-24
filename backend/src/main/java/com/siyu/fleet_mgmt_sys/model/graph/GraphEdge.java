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
 * All roads are assumed to be two-way in nature.
 */
public class GraphEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "edge_seq")
    @SequenceGenerator(name = "edge_seq", sequenceName = "graph_edge_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id")
    private GraphNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id")
    private GraphNode toNode;

    private String linkId;
    private Double lengthMetres;
    private Integer currentSpeedBand;
    private Double travelTimeSeconds;
}
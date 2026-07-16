package com.siyu.fleet_mgmt_sys.model.graph;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "graph_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphNode {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "node_seq")
    @SequenceGenerator(name = "node_seq", sequenceName = "graph_node_seq", allocationSize = 50)
    private Long id;

    private Double latitude;
    private Double longitude;
}
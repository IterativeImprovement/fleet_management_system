package com.siyu.fleet_mgmt_sys.model.simulation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_runs")
@Getter
@Setter
@NoArgsConstructor
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long seed;
    private double durationSeconds;
    private LocalDateTime createdAt = LocalDateTime.now();
}
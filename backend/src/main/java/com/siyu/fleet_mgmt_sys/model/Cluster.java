package com.siyu.fleet_mgmt_sys.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Cluster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double centroidLat;
    private double centroidLng;

    @ManyToOne(fetch = FetchType.LAZY)
    private Task topStandardTask; // this is the task that will be assigned to a Standard Robot looking for a task to pick up within a specific cluster

    @ManyToOne(fetch = FetchType.LAZY)
    private Task topLargeTask;

    @OneToMany(mappedBy = "startCluster")
    private List<Task> startTasks = new ArrayList<>(); // tasks that start in the cluster

    @OneToMany(mappedBy = "endCluster")
    private List<Task> endTasks = new ArrayList<>(); // task that end in the cluster

    // adjacent clusters （pre-computed on cluster creation)
    @ManyToMany
    @JsonIgnore
    private List<Cluster> adjacentClusters = new ArrayList<>();
}
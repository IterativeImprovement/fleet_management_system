package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tasks")

public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    private int priority;

    private String name;
    private String description;

    @ManyToOne
    @JoinColumn(name = "robot_id")
    private Robot robot;
}
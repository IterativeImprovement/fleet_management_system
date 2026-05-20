package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "robots")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) // All subclasses of robot go into the same table
@DiscriminatorColumn(name = "robot_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class Robot {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private int type;
    private int status;
    private double speed; // speed in metres per second

    /* Status Codes
    0: Idle and stationary, ready to pick up new tasks
    1: Normal, moving
    2: Executing a task (At task point)
    5: Low battery, moving
    9: Broken down, moving
     */

    @OneToOne(mappedBy = "robot", cascade = CascadeType.ALL)
    private Route route;

    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    private List<Task> tasks;

    protected Robot(String name, int type, double speed) {
        this.name = name;
        this.type = type;
        this.status = 0;
        this.speed = speed;
        this.route = null;
        this.tasks = null;
    }

    public int getStatus() {
        return this.status;
    }

    public Route getRoute() {
        return this.route;
    }

    public List<Task> getTasks() {
        return this.tasks == null ? Collections.emptyList() : Collections.unmodifiableList(this.tasks); //immutable
    }

}
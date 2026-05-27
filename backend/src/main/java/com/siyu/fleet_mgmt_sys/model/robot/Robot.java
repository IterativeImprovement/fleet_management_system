package com.siyu.fleet_mgmt_sys.model.robot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.siyu.fleet_mgmt_sys.model.Cluster;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "robots")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) // All subclasses of robot go into the same table
@DiscriminatorColumn(name = "robot_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class Robot {

    /* fields */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    protected String name;
    protected RobotStatus status = RobotStatus.IDLE;

    protected RobotType TYPE = RobotType.UNINITIALISED;
    protected double SPEED = 0.0; // speed in metres per second

    // for testing purposes, base is set at Harbourfront MRT station
    // TODO: Allow user to set base latlng
    protected double baseLatitude = 1.265389;
    protected double baseLongitude = 103.821530;

    protected double latitude;
    protected double longitude;

    public static int TOTAL_ROBOT_TYPES = 2; // number of types of robots

    /* other entities */

    @JsonIgnore
    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    protected List<Task> tasks; // tasks assigned to the robot

    @ManyToOne
    private Cluster currentCluster;

    /* methods */

    protected Robot(String name) {
        this.name = name;
    }

    public RobotType getType() {
        return RobotType.UNINITIALISED;
    }

    public List<Task> getTasks() {
        if (this.tasks == null) this.tasks = new ArrayList<>();
        return this.tasks;
    }

    public void setPosition(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "Robot {" +
                "\n  id: " + this.id +
                "\n  name: " + name +
                "}";
    }

    public String toStringDetailed() {
        return "Robot {" +
                "\n  id: " + this.id +
                "\n  name: " + name +
                "\n  type: " + TYPE +
                "\n  status: " + status +
                "\n  speed: " + SPEED +
                "\n latlong: {" + latitude + ", " + longitude + "}" +
                "\n  tasks: " + (tasks != null ? tasks.size() + " task(s)" : "none") +
                "\n}";
    }

}
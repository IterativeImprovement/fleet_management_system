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
    protected Long id;

    protected String name;
    protected String type;
    protected Integer status;
    protected double speed; // speed in metres per second

    /* Status Codes
    0: Idle and stationary, ready to pick up new tasks
    1: Normal, moving
    2: Executing a task (At task point)
    5: Low battery, moving
    9: Broken down, moving
     */

    @OneToOne(mappedBy = "robot", cascade = CascadeType.ALL)
    protected Route route;

    @OneToMany(mappedBy = "robot", cascade = CascadeType.ALL)
    protected List<Task> tasks;

    protected double latitude;
    protected double longitude;

    protected Robot(String name, String type, double speed) {
        this.name = name;
        this.type = type;
        this.status = 0;
        this.speed = speed;
        this.route = null;
        this.tasks = null;
        this.latitude = 0;
        this.longitude = 0;
    }

    public List<Task> getTasks() {
        return this.tasks == null ? Collections.emptyList() : Collections.unmodifiableList(this.tasks); //immutable
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
                "\n  type: " + type +
                "\n  status: " + status +
                "\n  speed: " + speed +
                "\n latlong: {" + latitude + ", " + longitude + "}" +
                "\n  route: " + (route != null ? route.getId() : "none") +
                "\n  tasks: " + (tasks != null ? tasks.size() + " task(s)" : "none") +
                "\n}";
    }

}
package com.siyu.fleet_mgmt_sys.model.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.siyu.fleet_mgmt_sys.model.Location;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    /* fields */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NonNull
    private Long id;

    @NonNull
    private Integer priority;
    @NonNull
    private String name;
    @NonNull
    private String description;

    private TaskType type;

    private LocalDateTime startDateTime;
    private LocalDateTime completionDateTime;

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    @JsonIgnore
    private Map<RobotType, Double> calculatedPriorities = new HashMap<>();

    private TaskStatus status;

    /* other entities */

    @OneToOne(cascade = CascadeType.ALL)
    private Route route;

    @ManyToOne
    @JoinColumn(name = "start_waypoint_id")
    @NonNull
    private WayPoint startWayPoint;

    @ManyToOne
    @JoinColumn(name = "end_waypoint_id")
    @NonNull
    private WayPoint endWayPoint;

    @ManyToOne
    @JoinColumn(name = "start_location_id")
    private Location startLocation;

    @ManyToOne
    @JoinColumn(name = "end_location_id")
    private Location endLocation;

    @ManyToOne
    @JoinColumn(name = "robot_id")
    private Robot robot;

    @ManyToMany
    @JoinTable(
            name = "task_dependencies",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    private List<Task> dependencies = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private Cluster startCluster;

    @ManyToOne(fetch = FetchType.LAZY)
    private Cluster endCluster;

    /* methods */

    public void setDependencies(List<Task> tasks) {
        this.dependencies = tasks != null ? tasks : new ArrayList<>();
    }

    public double getPriorityFor(RobotType robotType) {
        return this.calculatedPriorities.get(robotType);
    }

    @Override
    public String toString() {
        return "Task { id: " + id + ", name: " + name + " }\n";
    } // this makes it easier to read a lists of tasks
}
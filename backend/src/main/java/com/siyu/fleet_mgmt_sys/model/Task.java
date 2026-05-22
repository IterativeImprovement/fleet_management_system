package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
// it is assumed that the only possible null field is robot
public class Task {
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

    private String type;

    private LocalDateTime startDateTime;
    private LocalDateTime completionDateTime;

    @OneToOne
    @JoinColumn(name = "start_waypoint_id")
    @NonNull
    private WayPoint startWayPoint;

    @OneToOne
    @JoinColumn(name = "end_waypoint_id")
    @NonNull
    private WayPoint endWayPoint;

    @ManyToOne
    @JoinColumn(name = "robot_id")
    private Robot robot;

    @ManyToMany
    @JoinTable(
            name = "task_dependencies",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    private List<Task> tasks = new ArrayList<>();

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "Task { id: " + id + ", name: " + name + " }\n";
    } // this makes it easier to read a lists of tasks

    public String toStringDetailed() {
        return "Task {\n" +
                "  id: " + id + "\n" +
                "  name: '" + name + "'\n" +
                "  priority: " + priority + "\n" +
                "  description: '" + description + "'\n" +
                "  type: '" + type + "'\n" +
                "  startDateTime: " + startDateTime + "\n" +
                "  completionDateTime: " + completionDateTime + "\n" +
                "  startWayPoint: " + (startWayPoint != null ? startWayPoint.toString() : "null") + "\n" +
                "  endWayPoint: " + (endWayPoint != null ? endWayPoint.toString() : "null") + "\n" +
                "  robot: " + (robot != null ? robot.toString() : "null") + "\n" +
                "  dependencies:  " + tasks.toString() +
                "}";
    }
}
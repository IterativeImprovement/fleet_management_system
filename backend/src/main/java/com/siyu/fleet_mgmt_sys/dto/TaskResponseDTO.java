package com.siyu.fleet_mgmt_sys.dto;

import com.siyu.fleet_mgmt_sys.model.Task;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class TaskResponseDTO {
    private Long id;
    private String name;
    private String description;
    private String type;
    private int priority;
    private LocalDateTime startDateTime;
    private LocalDateTime completionDateTime;
    private String startWayPointStr;
    private String endWayPointStr;
    private String status;
    private Long robotId;
    private List<Long> dependencyIds;

    public TaskResponseDTO(Task task) {
        this.id = task.getId();
        this.name = task.getName();
        this.description = task.getDescription();
        this.type = task.getType() != null ? task.getType().name() : "STANDARD";
        this.priority = task.getPriority();
        this.startDateTime = task.getStartDateTime();
        this.completionDateTime = task.getCompletionDateTime();

        this.startWayPointStr = task.getStartWayPoint() != null ?
                task.getStartWayPoint().getLatitude() + "," + task.getStartWayPoint().getLongitude() : null;
        this.endWayPointStr = task.getEndWayPoint() != null ?
                task.getEndWayPoint().getLatitude() + "," + task.getEndWayPoint().getLongitude() : null;

        this.status = task.getStatus() != null ? task.getStatus().name() : "PENDING_ASSIGNMENT";
        this.robotId = task.getRobot() != null ? task.getRobot().getId() : null;
        this.dependencyIds = task.getDependencies() != null ?
                task.getDependencies().stream().map(Task::getId).toList() : List.of();
    }

}
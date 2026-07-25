package com.siyu.fleet_mgmt_sys.dto.task;

import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@MappedSuperclass
@Data
@NoArgsConstructor
public class TaskRequestDTO {
    private String name;
    private String description;
    private String type;
    private String status;
    private Integer priority;
    private LocalDateTime startDateTime; // 2026-05-21T14:49:33
    private LocalDateTime completionDateTime;
    private Long startLocationId;
    private Long endLocationId;
    private String startWayPointStr;  // "1.3081,103.8551"
    private String endWayPointStr;    // "1.2739,103.8012"
    private List<Long> dependencyIds = new ArrayList<>();
    private Double taskDuration;

    private Long simulationId;
}


/* sample data

  {
        "name": "Tuas shipment",
        "description": "Transport from Tuas to Harbourfront.",
        "type": "StandardTransport",
        "priority": 1,
        "startDateTime": "2026-05-21T18:00:00",
        "completionDateTime": "2026-05-21T19:30:00",
        "startWayPointStr": "1.3081,103.8551",
        "endWayPointStr": "1.2739,103.8012"
        }

 */

package com.siyu.fleet_mgmt_sys.dto.task;

import com.siyu.fleet_mgmt_sys.model.KeyLocations;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class RobotBreakdownTaskDTO extends TaskRequestDTO {

    public RobotBreakdownTaskDTO(Robot robot) {
        this.setName("BREAKDOWN " + robot.getName());
        this.setDescription(this.getName() + " , sending for servicing.");
        this.setType("Large");
        this.setPriority(1);
        this.setStartDateTime(LocalDateTime.now());
        this.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        this.setStartWayPointStr(robot.getLatitude() + "," + robot.getLongitude());
        this.setEndWayPointStr(KeyLocations.repairLatitude + "," + KeyLocations.repairLongitude);
    }

    public RobotBreakdownTaskDTO(Robot robot, Long simulationId) {
        this.setName("BREAKDOWN " + robot.getName());
        this.setDescription(this.getName() + " , sending for servicing.");
        this.setType("Large");
        this.setPriority(1);
        this.setStartDateTime(LocalDateTime.now());
        this.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        this.setStartWayPointStr(robot.getLatitude() + "," + robot.getLongitude());
        this.setEndWayPointStr(KeyLocations.repairLatitude + "," + KeyLocations.repairLongitude);
        this.setSimulationId(simulationId);
    }



}

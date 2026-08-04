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
        this.setTargetRobotId(robot.getId());
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
        this.setTargetRobotId(robot.getId());
    }

    /**
     * Fallback-aware constructor: takes an explicit start position rather than always trusting
     * robot.getLatitude()/getLongitude(). Use this when the robot's live telemetry position hasn't
     * arrived yet (e.g. malfunction fired before the first WebSocket position push) - the caller is
     * expected to have substituted a safe fallback (typically the robot's base position) in that case.
     */
    public RobotBreakdownTaskDTO(Robot robot, Long simulationId, double startLat, double startLon) {
        this.setName("BREAKDOWN " + robot.getName());
        this.setDescription(this.getName() + " , sending for servicing.");
        this.setType("Large");
        this.setPriority(1);
        this.setStartDateTime(LocalDateTime.now());
        this.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        this.setStartWayPointStr(startLat + "," + startLon);
        this.setEndWayPointStr(KeyLocations.repairLatitude + "," + KeyLocations.repairLongitude);
        this.setSimulationId(simulationId);
        this.setTargetRobotId(robot.getId());
    }

}

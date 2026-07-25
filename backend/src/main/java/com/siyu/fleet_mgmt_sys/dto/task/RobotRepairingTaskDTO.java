package com.siyu.fleet_mgmt_sys.dto.task;

import com.siyu.fleet_mgmt_sys.model.KeyLocations;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;

import java.time.LocalDateTime;

public class RobotRepairingTaskDTO extends TaskRequestDTO{

    public RobotRepairingTaskDTO(Robot robot) {
        this.setName("REPAIR " + robot.getName());
        this.setDescription(this.getName() + " is being repaired.");
        this.setType(robot.getType().toString());
        this.setPriority(1);
        this.setStartDateTime(LocalDateTime.now());
        this.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        this.setStartWayPointStr(KeyLocations.repairLatitude + ", " + KeyLocations.repairLongitude);
        this.setEndWayPointStr(KeyLocations.repairLatitude + ", " + KeyLocations.repairLongitude);
        this.setTaskDuration(7200.0);
    }

    public RobotRepairingTaskDTO(Robot robot, Long simulationId) {
        this.setName("REPAIR " + robot.getName());
        this.setDescription(this.getName() + " is being repaired");
        this.setType("Large");
        this.setPriority(1);
        this.setStartDateTime(LocalDateTime.now());
        this.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        this.setStartWayPointStr(KeyLocations.repairLatitude + ", " + KeyLocations.repairLongitude);
        this.setStartWayPointStr(KeyLocations.repairLatitude + ", " + KeyLocations.repairLongitude);
        this.setTaskDuration(7200.0); // two hours
        this.setSimulationId(simulationId);
    }

}

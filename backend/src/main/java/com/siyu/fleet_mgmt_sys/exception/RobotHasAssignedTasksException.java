package com.siyu.fleet_mgmt_sys.exception;

public class RobotHasAssignedTasksException extends RuntimeException {
    public RobotHasAssignedTasksException(Long robotId) {
        super("Cannot delete robot " + robotId + " with assigned tasks");
    }
}

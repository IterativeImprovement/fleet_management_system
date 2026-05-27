package com.siyu.fleet_mgmt_sys.exception;

public class RobotNotFoundException extends RuntimeException {
    public RobotNotFoundException(Long id) {
        super("Robot with id " + id + " not found");
    }
}

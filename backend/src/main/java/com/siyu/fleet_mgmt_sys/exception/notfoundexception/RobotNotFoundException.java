package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class RobotNotFoundException extends NotFoundException {
    public RobotNotFoundException(Long id) {
        super("Robot with id " + id + " not found");
    }
}

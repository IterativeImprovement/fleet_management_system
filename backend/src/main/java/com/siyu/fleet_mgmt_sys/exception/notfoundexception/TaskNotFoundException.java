package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class TaskNotFoundException extends NotFoundException {
    public TaskNotFoundException(Long id) {
        super("Task with id " + id + " not found");
    }
}

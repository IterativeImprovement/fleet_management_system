package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class RoadNotFoundException extends NotFoundException {
    public RoadNotFoundException(String id) {
        super("Road id " + id + " not found");
    }
}


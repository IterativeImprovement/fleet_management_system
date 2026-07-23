package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class RouteNotFoundException extends NotFoundException {
    public RouteNotFoundException(Long startid, Long goalId) {
        super("No route found between nodes " + startid + " and " + goalId);
    }
}

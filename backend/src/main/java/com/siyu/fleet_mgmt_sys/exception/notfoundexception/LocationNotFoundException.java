package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class LocationNotFoundException extends NotFoundException {
    public LocationNotFoundException(Long id) {
        super("Location with id " + id + " not found");
    }
}

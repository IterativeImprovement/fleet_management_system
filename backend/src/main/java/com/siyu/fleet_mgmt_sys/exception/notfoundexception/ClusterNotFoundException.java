package com.siyu.fleet_mgmt_sys.exception.notfoundexception;

public class ClusterNotFoundException extends NotFoundException{
    public ClusterNotFoundException(Long id) { super("Cluster with id " + id + " not found");
    }
}

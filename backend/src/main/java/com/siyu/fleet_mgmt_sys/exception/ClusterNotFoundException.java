package com.siyu.fleet_mgmt_sys.exception;

public class ClusterNotFoundException extends RuntimeException{
    public ClusterNotFoundException(Long id) { super("Cluster with id " + id + " not found");
    }
}

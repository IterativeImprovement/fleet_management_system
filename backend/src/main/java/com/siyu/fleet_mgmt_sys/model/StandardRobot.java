package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("Standard")

public class StandardRobot extends Robot {

    protected StandardRobot() {} // for use by JPA

    public StandardRobot(String name) {
        super("S" + name,0);
    }

}
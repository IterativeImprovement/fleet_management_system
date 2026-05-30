package com.siyu.fleet_mgmt_sys.model.robot;


import com.siyu.fleet_mgmt_sys.model.enums.RobotType;

// stores all the static attributes relevant to the various classes
public class RobotAttributes {

    public static class Standard {
        public static final RobotType TYPE = RobotType.STANDARD;
        public static final double SPEED = 10.0;
        public static final String DISCRIMINATOR = "Standard";
    }

    public static class Large {
        public static final RobotType TYPE = RobotType.LARGE;
        public static final double SPEED = 5.0;
        public static final String DISCRIMINATOR = "Large";
    }
}

package com.siyu.fleet_mgmt_sys.model.robot;

import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("Large")
@Getter
@Setter
@NoArgsConstructor
public class LargeRobot extends Robot {

    public LargeRobot(String name) {
        super("L" + name);
    }


    public final static RobotType TYPE = RobotType.LARGE;
    public final static double SPEED = 5.0;

    @Override
    public RobotType getType() {
        return LargeRobot.TYPE;
    }
}
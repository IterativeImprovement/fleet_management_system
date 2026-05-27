package com.siyu.fleet_mgmt_sys.model.robot;

import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("Standard")
@Getter
@Setter
@NoArgsConstructor
public class StandardRobot extends Robot {

    public StandardRobot(String name) {
        super("S" + name);
    }

    public final static RobotType TYPE = RobotType.STANDARD;
    public final static double SPEED = 10.0;

    @Override
    public RobotType getType() {
        return StandardRobot.TYPE;
    }
}
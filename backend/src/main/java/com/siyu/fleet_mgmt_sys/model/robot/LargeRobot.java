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
        this.type = RobotType.LARGE;
        this.speed = RobotAttributes.Large.SPEED;
    }

}
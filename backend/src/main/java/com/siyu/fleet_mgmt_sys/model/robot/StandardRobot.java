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
        this.type = RobotType.STANDARD;
        this.speed = RobotAttributes.Standard.SPEED;
    }
}
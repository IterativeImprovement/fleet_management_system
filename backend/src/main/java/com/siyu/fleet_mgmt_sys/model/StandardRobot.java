package com.siyu.fleet_mgmt_sys.model;

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
        super("S" + name,0, 10.0);
    }

    @Override
    public String toString() {
        return "Robot {" +
                "\n  id: " + this.id +
                "\n  name: " + name +
                "\n  type: " + type +
                "\n  status: " + status +
                "\n  speed: " + speed +
                "\n  route: " + (route != null ? route.getId() : "none") +
                "\n  tasks: " + (tasks != null ? tasks.size() + " task(s)" : "none") +
                "\n}";
    }
}
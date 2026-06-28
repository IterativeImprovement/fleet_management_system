package com.siyu.fleet_mgmt_sys.service.route;


import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.robot.RobotAttributes;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
@NoArgsConstructor
public class RouteEstimationService { // estimates the time taken to complete a task

    public void updateRouteEstimatedTimes(Route route) {
        // simplified version
        int totalDistance = route.getTotalDistance();
        Map<RobotType, Double> estimatedTimes = new EnumMap<>(RobotType.class);
        estimatedTimes.put(RobotType.STANDARD, totalDistance / RobotAttributes.Standard.SPEED);
        estimatedTimes.put(RobotType.LARGE, totalDistance / RobotAttributes.Large.SPEED);

        route.setEstimatedTimes(estimatedTimes);

        // TODO: consider traffic, integrate LTA API
    }
}

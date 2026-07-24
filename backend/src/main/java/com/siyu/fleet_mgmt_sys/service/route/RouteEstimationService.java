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
        // simplified version: free-flow time = distance(m) / max speed, converting km/h -> m/s
        int totalDistance = route.getTotalDistance();
        double stdMps   = RobotAttributes.Standard.SPEED / 3.6;
        double largeMps = RobotAttributes.Large.SPEED / 3.6;
        Map<RobotType, Double> estimatedTimes = new EnumMap<>(RobotType.class);
        estimatedTimes.put(RobotType.STANDARD, totalDistance / stdMps);
        estimatedTimes.put(RobotType.LARGE, totalDistance / largeMps);

        route.setEstimatedTimes(estimatedTimes);

        // TODO: consider traffic, integrate LTA API
    }
}

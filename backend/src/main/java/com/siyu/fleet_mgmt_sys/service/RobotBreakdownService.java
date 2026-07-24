package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Robot breakdown services handles robot breakdown.
 * When a robot breaks down, its current tasks are immediately returned to the common pool to be reallocated.
 * A high priority task for Large Robots is created into the common pool called "Tow {robot_id} for repair".
 *
 */
@Service
@RequiredArgsConstructor
public class RobotBreakdownService {

    private RobotRepository robotRepository;
    public void handleRobotBreakdown(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

    }

}

package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Robot breakdown services handles robot breakdown.
 * When a robot breaks down, its current tasks are immediately returned to the common pool to be reallocated.
 * A high priority task for Large Robots is created into the common pool called "Tow {robot_id} for repair".
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotBreakdownService {

    private final RobotRepository robotRepository;

    public void handleRobotBreakdown(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        // TODO (routing branch, in progress): return current tasks to the common pool and
        // raise a high-priority "Tow {robot_id} for repair" task for Large robots.
        log.warn("BREAKDOWN EVENT: robotId={} status={} — handler is still a stub",
                robot.getId(), robot.getStatus());
    }

}

package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles a robot breakdown: cancel its dispatch, return its task(s) to the common pool, mark it
 * ERROR, then re-run allocation so another robot picks up the dropped work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotBreakdownService {

    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final DispatchService dispatchService;

    @Transactional
    public void handleRobotBreakdown(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
        Long simulationId = robot.getSimulationId();

        // Stop dispatching to this robot — any in-flight arrival for it is now void.
        dispatchService.cancelDispatch(robotId);

        // Return its task(s) to the common pool.
        List<Task> dropped = new ArrayList<>(robot.getTasks());
        for (Task task : dropped) {
            task.setRobot(null);
            task.setStatus(TaskStatus.PENDING_ASSIGNMENT);
            taskRepository.save(task);
        }
        robot.getTasks().clear();
        robot.setStatus(RobotStatus.ERROR);
        robotRepository.save(robot);

        log.warn("BREAKDOWN: robot {} returned {} task(s) to the pool", robotId, dropped.size());

        // ponytail: high-priority "Tow {robot} for repair" task deferred — reassigning the dropped
        // work is the behaviour that matters for the sim.
        if (simulationId != null) {
            dispatchService.allocateAndDispatch(simulationId);
        }
    }
}

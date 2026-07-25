package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.task.RobotBreakdownTaskDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.service.task.TaskService;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


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
    private TaskService taskService;

    public void handleRobotBreakdown(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        List<Task> assignedTasks =  robot.getTasks();

        log.info("Unassigning {} tasks from robot {}", assignedTasks.size(), robot.getName());

        // Releases tasks into common pool
        if (!assignedTasks.isEmpty()) {
            assignedTasks.stream().peek(task -> {
                task.setStatus(TaskStatus.PENDING_ASSIGNMENT);
                task.setRobot(null);
            }).close();

            robot.setTasks(new ArrayList<>());
        }

        // Creates a large robot task to tow
        if (robot.isSimulated()) {
            taskService.createTask(new RobotBreakdownTaskDTO(robot, robot.getSimulationId()));
        } else {
            taskService.createTask(new RobotBreakdownTaskDTO(robot));
        }


    }

}

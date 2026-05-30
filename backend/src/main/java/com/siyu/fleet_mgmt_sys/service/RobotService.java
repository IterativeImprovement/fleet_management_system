package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.exception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.exception.TaskNotFoundException;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import com.siyu.fleet_mgmt_sys.specification.RobotSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RobotService {
    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final TaskAllocationService allocationService;

    public Robot createRobot(RobotRequestDTO req) {
        Robot robot = switch (req.getType()) {
            case "Standard" -> new StandardRobot(req.getName());
            case "Large" -> new LargeRobot(req.getName());
            default -> throw new IllegalArgumentException("Unknown robot type: " + req.getType());
        };

        // initialises the robot with the given list of tasks
        req.getTaskIdsToAdd().stream().map(id -> taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id)))
                                .forEach(task -> allocationService.assign(robot, task, false));


        Robot savedRobot = robotRepository.save(robot);
        System.out.println("Robot created successfully!\n" + savedRobot.toStringDetailed());
        return savedRobot;
    }

    public Robot getRobot(Long id) {
        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));
        System.out.println("Robot retrieved successfully!\n" + robot.toStringDetailed());
        return robot;
    }

    public void deleteRobot(Long id) {
        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));

        if (!robot.getTasks().isEmpty()) {
            throw new IllegalStateException("Cannot delete robot with assigned tasks");
        }
        //String robotString = robot.toStringDetailed();
        robotRepository.delete(robot);

        // System.out.println("Robot deleted successfully!\n" + robotString);
        System.out.println("Robot deleted successfully!\n");
    }

    public Robot updateRobot(Long id, RobotRequestDTO req) {
        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));

        if (req.getName() != null) robot.setName(req.getName());

        if (req.getTasksIdsToRemove() != null && !req.getTasksIdsToRemove().isEmpty()) {
            req.getTasksIdsToRemove().forEach(taskId -> {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new TaskNotFoundException(taskId));

                robot.getTasks().remove(task);

                task.setRobot(null);
                task.setStatus(TaskStatus.PENDING_ASSIGNMENT); // return to pool
                taskRepository.save(task);
            });
        }

        if (req.getTaskIdsToAdd() != null && !req.getTaskIdsToAdd().isEmpty()) {
            req.getTaskIdsToAdd().forEach(taskId -> {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new TaskNotFoundException(taskId));

                // add to robot's list
                robot.getTasks().add(task);

                // assign robot to task
                task.setRobot(robot);
                task.setStatus(TaskStatus.ASSIGNED);

                taskRepository.save(task);
            });
        }

        return robotRepository.save(robot);
    }

    public List<Robot> filterRobots(String status, String type, List<Long> taskIds) {
        RobotStatus robotStatus = status != null ? RobotStatus.valueOf(status.toUpperCase()) : null; // convert to RobotStatus

        List<Robot> robots = robotRepository.findAll(
                RobotSpecification.filter(robotStatus, type, taskIds)
        );

        System.out.println("Retrieved filtered robots: " + robots);
        return robots;
    }

    public void setToBase(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        robot.setLatitude(robot.getBaseLatitude());
        robot.setLongitude(robot.getBaseLongitude());
        robot.setStatus(RobotStatus.IDLE);
        robotRepository.save(robot);

        // robot is idle at base - trigger allocation
        allocationService.assignNextTask(robot);
    }


    // websocket
    public void updateStatusAndPosition(Long robotId, RobotStatus status, double lat, double lng) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        robot.setPosition(lat, lng);
        robot.setStatus(status);

        robotRepository.save(robot);
    }
}
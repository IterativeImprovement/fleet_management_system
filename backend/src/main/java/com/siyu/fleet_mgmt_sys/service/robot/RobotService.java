package com.siyu.fleet_mgmt_sys.service.robot;

import com.siyu.fleet_mgmt_sys.dto.robot.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.exception.RobotHasAssignedTasksException;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.TaskNotFoundException;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import com.siyu.fleet_mgmt_sys.specification.RobotSpecification;
import jakarta.transaction.Transactional;
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

        // new robots start parked at base, not at the default (0,0)
        robot.setPosition(robot.getBaseLatitude(), robot.getBaseLongitude());

        // initialises the robot with the given list of tasks
        req.getTaskIdsToAdd().stream().map(id -> taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id)))
                                .forEach(task -> allocationService.assign(robot, task, false));


        Robot savedRobot = robotRepository.save(robot);
        System.out.println("Robot created successfully!\n" + savedRobot.toStringDetailed());
        return savedRobot;
    }

    @Transactional
    public Robot getRobot(Long id) {
        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));
        robot.getTasks().size(); // initialise lazy tasks collection within the session
        System.out.println("Robot retrieved successfully!\n" + robot.toStringDetailed());
        return robot;
    }

    @Transactional
    public void deleteRobot(Long id) {
        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));

        if (!robot.getTasks().isEmpty()) {
            throw new RobotHasAssignedTasksException(id);
        }

        robotRepository.delete(robot);

        System.out.println("Robot" + id +  "deleted successfully!\n");
    }

    @Transactional
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

        Robot saved = robotRepository.save(robot);
        saved.getTasks().size(); // initialise lazy tasks so the response can be mapped after the transaction
        return saved;
    }

    @Transactional
    public List<Robot> filterRobots(String status, String type, List<Long> taskIds) {
        RobotStatus robotStatus = status != null ? RobotStatus.valueOf(status.toUpperCase()) : null; // convert to RobotStatus

        List<Robot> robots = robotRepository.findAll(
                RobotSpecification.filter(robotStatus, type, taskIds)
        );

        robots.forEach(robot -> robot.getTasks().size()); // initialise lazy tasks within the session

        System.out.printf("Retrieved filtered %d robots%n", robots.size());
        return robots;
    }

    @Transactional
    public void setToBase(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        robot.setLatitude(robot.getBaseLatitude());
        robot.setLongitude(robot.getBaseLongitude());
        robot.setStatus(RobotStatus.IDLE);
        robotRepository.save(robot);
        // re-allocation is driven by DispatchService (backend-authoritative dispatch)
    }


    // websocket telemetry - position only; the backend owns status via dispatch
    @Transactional
    public void updatePosition(Long robotId, double lat, double lng) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        robot.setPosition(lat, lng);
        robotRepository.save(robot);
    }
}

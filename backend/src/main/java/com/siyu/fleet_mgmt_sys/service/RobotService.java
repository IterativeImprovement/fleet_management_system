package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.exception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.Robot;
import com.siyu.fleet_mgmt_sys.model.StandardRobot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.specification.RobotSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RobotService {
    private final RobotRepository robotRepository;

    public Robot createRobot(RobotRequestDTO req) {
        Robot robot = switch (req.getType()) {
            case "Standard" -> new StandardRobot(req.getName());
            case "Large" -> new LargeRobot(req.getName());
            default -> throw new IllegalArgumentException("Unknown robot type: " + req.getType());
        };

        System.out.println("Robot created successfully!\n" + robot.toStringDetailed());
        return robotRepository.save(robot);
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
        String robotString = robot.toStringDetailed();
        robotRepository.delete(robot);

        System.out.println("Robot deleted successfully!\n" + robotString);
    }

    public List<Robot> filterRobots(Integer status, String type, List<Long> taskIds) {
        List<Robot> robots = robotRepository.findAll(
                RobotSpecification.filter(status, type, taskIds)
        );

        System.out.println("Retrieved filtered robots: " + robots);
        return robots;
    }

    public void updateStatusAndPosition(Long robotId, Integer status, double lat, double lng) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        robot.setPosition(lat, lng);
        robot.setStatus(status);

        robotRepository.save(robot);
    }
}
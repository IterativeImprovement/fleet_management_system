package com.siyu.fleet_mgmt_sys.service.robot;

import com.siyu.fleet_mgmt_sys.dto.robot.RobotResponseDTO;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RobotMapper {

    public RobotResponseDTO toDTO(Robot robot) {
        if (robot == null) {
            return null;
        }

        RobotResponseDTO dto = new RobotResponseDTO();

        dto.setId(robot.getId());
        dto.setName(robot.getName());
        dto.setStatus(robot.getStatus());
        dto.setType(robot.getType());
        dto.setSpeed(robot.getSpeed());
        dto.setBaseLatitude(robot.getBaseLatitude());
        dto.setBaseLongitude(robot.getBaseLongitude());
        dto.setLatitude(robot.getLatitude());
        dto.setLongitude(robot.getLongitude());
        dto.setSimulationId(robot.getSimulationId());

        if (robot.getCurrentCluster() != null) {
            dto.setCurrentClusterId(robot.getCurrentCluster().getId());
        } else {
            dto.setCurrentClusterId(null);
        }

        if (robot.getTasks() != null) {
            List<Long> taskIds = robot.getTasks().stream()
                    .map(Task::getId)
                    .collect(Collectors.toList());
            dto.setTaskIds(taskIds);
        } else {
            dto.setTaskIds(new ArrayList<>());
        }

        return dto;
    }
}
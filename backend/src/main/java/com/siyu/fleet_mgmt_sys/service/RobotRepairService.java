package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.task.RobotRepairingTaskDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.KeyLocations;
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

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotRepairService {

    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final WebsocketPublisherService websocketPublisherService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final DispatchService dispatchService;

    private static final double SIM_TIME_COMPRESSION_FACTOR = 259200.0 / 120.0;

    /**
     * Starts repair after a towing task reaches the workshop and releases the tow
     * robot.
     */
    public void startRepair(Task breakdownTask, Robot robotB) {
        // breakdown task name is "BREAKDOWN <robotName>"
        String robotName = breakdownTask.getName().replace("BREAKDOWN ", "").trim();

        Robot robotA = robotRepository.findByName(robotName).orElseThrow(() -> new RobotNotFoundException(robotName));

        beginRepair(robotA);

        // Free the tow robot back into the fleet now that it's dropped off Robot A.
        robotB.setStatus(RobotStatus.IDLE);
        robotRepository.save(robotB);
        if (robotA.getSimulationId() != null) {
            dispatchService.allocateAndDispatch(robotA.getSimulationId());
        }
        log.info("Robot {} released back to fleet after towing", robotB.getName());
    }

    /** Starts repair for a robot that travelled to servicing itself. */
    public void startSelfRepair(Robot robot) {
        beginRepair(robot);
    }

    /** Creates the repair task and schedules the robot's release. */
    private void beginRepair(Robot robotA) {
        Long simulationId = robotA.getSimulationId();

        RobotRepairingTaskDTO repairDTO = new RobotRepairingTaskDTO(robotA, simulationId);

        Task repairTask = new Task();
        repairTask.setName(repairDTO.getName());
        repairTask.setDescription(repairDTO.getDescription());
        repairTask.setPriority(repairDTO.getPriority());
        repairTask.setTaskDuration(repairDTO.getTaskDuration());
        repairTask.setStatus(TaskStatus.IN_PROGRESS);
        repairTask.setSimulationId(repairDTO.getSimulationId());
        repairTask.setSimulated(repairDTO.getSimulationId() != null);
        taskRepository.save(repairTask);

        robotA.setStatus(RobotStatus.UNDER_MAINTENANCE);
        robotRepository.save(robotA);

        if (simulationId != null) {
            websocketPublisherService.publishRobotStatus(robotA.getId(), simulationId, RobotStatus.UNDER_MAINTENANCE);
        } else {
            websocketPublisherService.publishRobotStatus(robotA.getId(), RobotStatus.UNDER_MAINTENANCE);
        }

        double durationSeconds = repairDTO.getTaskDuration();
        double realDelaySeconds = simulationId != null
                ? Math.max(durationSeconds / SIM_TIME_COMPRESSION_FACTOR, 1.0)
                : durationSeconds;
        log.info("Repair started for robot {} - will complete in {} (2 simulated hours)",
                robotA.getName(), simulationId != null
                        ? String.format("%.1fs", realDelaySeconds)
                        : "2 hours");
        scheduler.schedule(
                () -> completeRepair(robotA.getId(), repairTask.getId()),
                Math.round(realDelaySeconds),
                TimeUnit.SECONDS);
    }

    /**
     * Called after the repair duration elapses.
     * Robot A is released back into the fleet.
     */
    private void completeRepair(Long robotId, Long repairTaskId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        Task repairTask = taskRepository.findById(repairTaskId)
                .orElseThrow(() -> new IllegalStateException("Repair task not found: " + repairTaskId));
        repairTask.setStatus(TaskStatus.COMPLETED);
        repairTask.setCompletionDateTime(LocalDateTime.now());
        taskRepository.save(repairTask);

        robot.setStatus(RobotStatus.IDLE);
        robot.setPosition(
                KeyLocations.repairLatitude,
                KeyLocations.repairLongitude);
        robotRepository.save(robot);

        log.info("Repair complete for robot {} - status set to IDLE", robot.getName());
        if (robot.getSimulationId() != null) {
            websocketPublisherService.publishRobotStatus(robotId, robot.getSimulationId(), RobotStatus.IDLE);
        } else {
            websocketPublisherService.publishRobotStatus(robotId, RobotStatus.IDLE);
        }
        websocketPublisherService.publishRepairComplete(robotId);

        if (robot.getSimulationId() != null) {
            dispatchService.allocateAndDispatch(robot.getSimulationId());
            dispatchService.sendToBaseIfIdle(robotId);
        }
    }
}
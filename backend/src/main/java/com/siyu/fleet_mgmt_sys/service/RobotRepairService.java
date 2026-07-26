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
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);
    private final DispatchService dispatchService;

    private static final double SIM_TIME_COMPRESSION_FACTOR = 259200.0 / 120.0;

    /**
     * Called when a RobotBreakdownTask completes — i.e. Robot B has arrived
     * at the repair location with Robot A.
     * Starts a 2-hour repair timer for Robot A.
     */
    public void startRepair(Task breakdownTask, Robot robotB) {
        // Robot A is identified from the breakdown task description
        // breakdown task name is "BREAKDOWN <robotName>"
        String robotName = breakdownTask.getName().replace("BREAKDOWN ", "").trim();

        Robot robotA = robotRepository.findByName(robotName).orElseThrow(() -> new RobotNotFoundException(robotName));

        Long simulationId = robotA.getSimulationId();
        // Create the repair task for Robot A
        RobotRepairingTaskDTO repairDTO = new RobotRepairingTaskDTO(robotA, simulationId);

        Task repairTask = new Task();
        repairTask.setName(repairDTO.getName());
        repairTask.setDescription(repairDTO.getDescription());
        repairTask.setPriority(repairDTO.getPriority());
        repairTask.setTaskDuration(repairDTO.getTaskDuration());
        repairTask.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(repairTask);

        // Robot A → UNDER_MAINTENANCE
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
        log.info("Repair started for robot {} — will complete in {} (2 simulated hours)",
                robotA.getName(), simulationId != null
                        ? String.format("%.1fs", realDelaySeconds)
                        : "2 hours");
        scheduler.schedule(
                () -> completeRepair(robotA.getId(), repairTask.getId()),
                Math.round(realDelaySeconds),
                TimeUnit.SECONDS);

        robotB.setStatus(RobotStatus.IDLE);
        robotRepository.save(robotB);
        if (simulationId != null) {
            dispatchService.allocateAndDispatch(simulationId);
        }
        log.info("Robot {} released back to fleet after towing", robotB.getName());
    }

    /**
     * Called after the repair duration elapses.
     * Robot A is released back into the fleet.
     */
    private void completeRepair(Long robotId, Long repairTaskId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));

        // Mark repair task complete
        Task repairTask = taskRepository.findById(repairTaskId)
                .orElseThrow(() -> new IllegalStateException("Repair task not found: " + repairTaskId));
        repairTask.setStatus(TaskStatus.COMPLETED);
        repairTask.setCompletionDateTime(LocalDateTime.now());
        taskRepository.save(repairTask);

        // Robot A → IDLE, ready for reassignment
        robot.setStatus(RobotStatus.IDLE);
        robot.setPosition(
                KeyLocations.repairLatitude,
                KeyLocations.repairLongitude);
        robotRepository.save(robot);

        log.info("Repair complete for robot {} — status set to IDLE", robot.getName());
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
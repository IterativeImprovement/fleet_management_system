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

    // FIX: taskDuration (7200s = "2 hours") is expressed on the same simulated-seconds timeline as
    // every other duration in the system (task deadlines, dispatch.etaSeconds) — it is NOT meant to
    // be real wall-clock seconds. Everything else gets compressed for a simulated run by the
    // frontend's speedFactor (default 259200 sim-seconds / 120 real-seconds = 2160x), but this
    // scheduler runs on real time with no knowledge of that factor, so a "2 hour" repair used to
    // take a literal 2 real hours — vastly outliving any demo session, making a repaired robot
    // indistinguishable from one that never recovers. Mirror the frontend's default compression
    // here for simulated runs. This is a known coupling: if the default speedFactor ever changes on
    // the frontend, update it here too (or better, thread the run's actual speedFactor through to
    // the backend so this isn't a hardcoded assumption).
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

        // FIX: this called the 2-arg publishRobotStatus(robotId, status), which publishes to
        // /topic/robot/{id}/status — a topic the frontend never subscribes to. It only listens on
        // /topic/simulation/{id}/repair (see useSimulationPlayback.js), which is only reachable via
        // the 3-arg overload. The transition into UNDER_MAINTENANCE was silently invisible on the
        // frontend as a result — same bug as the IDLE-after-repair publish below.
        if (simulationId != null) {
            websocketPublisherService.publishRobotStatus(robotA.getId(), simulationId, RobotStatus.UNDER_MAINTENANCE);
        } else {
            websocketPublisherService.publishRobotStatus(robotA.getId(), RobotStatus.UNDER_MAINTENANCE);
        }

        // Schedule Robot A's recovery after taskDuration (simulated) seconds. For a simulated run,
        // compress it the same way movement legs are compressed, so the repair actually finishes
        // within the demo's runtime; live (non-simulated) robots keep the real 2-hour duration.
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
        // FIX: same wrong-topic bug as UNDER_MAINTENANCE above — this must use the 3-arg overload
        // (/topic/simulation/{id}/repair) or the frontend never learns the robot left maintenance,
        // and its stale robotPositionOverrides entry keeps showing NEED_MAINTENANCE forever even
        // though the backend correctly flipped it to IDLE.
        if (robot.getSimulationId() != null) {
            websocketPublisherService.publishRobotStatus(robotId, robot.getSimulationId(), RobotStatus.IDLE);
        } else {
            websocketPublisherService.publishRobotStatus(robotId, RobotStatus.IDLE);
        }
        websocketPublisherService.publishRepairComplete(robotId);

        // FIX: being IDLE in the DB doesn't get the robot dispatched anywhere by itself — without
        // this it just sits at the repair depot until some unrelated event happens to trigger
        // allocation for this simulation.
        //
        // FIX: a repaired robot used to just stop here — becoming IDLE at the repair spot with no
        // further action if nothing was immediately pending for it. Every other "just finished
        // something" path (completing a task, returning from a tow) falls back to heading home when
        // there's nothing queued; a repaired robot should behave the same way instead of sitting at
        // the repair depot indefinitely.
        if (robot.getSimulationId() != null) {
            dispatchService.allocateAndDispatch(robot.getSimulationId());
            dispatchService.sendToBaseIfIdle(robotId);
        }
    }
}
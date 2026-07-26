package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.task.RobotBreakdownTaskDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.task.TaskService;
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
    private final TaskService taskService;
    private final WebsocketPublisherService webSocketPublisher;

    // Treat anything within this many degrees of (0,0) as "no real position yet" — WebSocket
    // telemetry hasn't landed, rather than the robot genuinely idling in the Gulf of Guinea.
    private static final double UNSET_POSITION_EPSILON = 1e-6;

    @Transactional
    public void handleRobotBreakdown(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
        Long simulationId = robot.getSimulationId();

        // FIX: the simulated event schedule can fire more than one malfunction for the same robot
        // (roughly 1 per robot per 14 sim-hours over a 3-day run means a robot can plausibly break
        // down again before it's even repaired). Re-running the full breakdown flow on a robot
        // that's already NEED_MAINTENANCE/UNDER_MAINTENANCE would cancel its in-progress tow/repair,
        // reset its status, and spin up a second, redundant breakdown+tow task for no reason.
        if (robot.getStatus() == RobotStatus.NEED_MAINTENANCE
                || robot.getStatus() == RobotStatus.UNDER_MAINTENANCE
                || robot.getStatus() == RobotStatus.ERROR) {
            log.info("BREAKDOWN: ignoring malfunction for robot {} — already {}",
                    robot.getName(), robot.getStatus());
            return;
        }

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

        // Robot A → NEED_MAINTENANCE (not ERROR — it's awaiting repair, not in a fault state)
        robot.setStatus(RobotStatus.NEED_MAINTENANCE);
        robotRepository.save(robot);

        log.warn("BREAKDOWN: robot {} returned {} task(s) to the pool, status → NEED_MAINTENANCE",
                robot.getName(), dropped.size());

        // Create a breakdown task — a tow robot (Robot B) will carry Robot A to the repair location.
        // The breakdown task is submitted to the pool; allocation assigns Robot B automatically.
        if (simulationId != null) {
            try {
                RobotBreakdownTaskDTO breakdownDTO = buildBreakdownTaskDTO(robot, simulationId);
                taskService.createTask(breakdownDTO);
                log.info("BREAKDOWN: created breakdown task for robot {}", robot.getName());
            } catch (Exception e) {
                log.error("BREAKDOWN: failed to create breakdown task for robot {}: {}",
                        robot.getName(), e.getMessage(), e);
            }

            // Re-run allocation — picks up both dropped tasks and the new breakdown task.
            dispatchService.allocateAndDispatch(simulationId);

            // Notify frontend so it can show the repair status
            webSocketPublisher.publishRobotStatus(robotId, simulationId, RobotStatus.NEED_MAINTENANCE);
        }
    }

    /**
     * Fallback: if the robot's live position hasn't been reported yet (still at the JPA default of
     * 0,0), routing the breakdown task from there would fail outright (outside Singapore bounds) —
     * silently dropping the tow task and leaving the robot stuck at NEED_MAINTENANCE forever. Fall
     * back to the robot's own base position instead, which is always a valid, in-bounds point.
     */
    private RobotBreakdownTaskDTO buildBreakdownTaskDTO(Robot robot, Long simulationId) {
        double lat = robot.getLatitude();
        double lon = robot.getLongitude();

        boolean unset = Math.abs(lat) < UNSET_POSITION_EPSILON && Math.abs(lon) < UNSET_POSITION_EPSILON;
        boolean outOfBounds = !unset && !taskService.isWithinSingapore(lat, lon);

        if (unset || outOfBounds) {
            log.warn("BREAKDOWN: robot {} has no valid live position ({}, {}) — falling back to its "
                            + "base position ({}, {}) for the tow task",
                    robot.getName(), lat, lon, robot.getBaseLatitude(), robot.getBaseLongitude());
            return new RobotBreakdownTaskDTO(robot, simulationId, robot.getBaseLatitude(), robot.getBaseLongitude());
        }

        return new RobotBreakdownTaskDTO(robot, simulationId, lat, lon);
    }
}
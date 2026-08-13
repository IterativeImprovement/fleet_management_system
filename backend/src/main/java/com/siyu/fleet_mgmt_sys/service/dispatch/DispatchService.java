package com.siyu.fleet_mgmt_sys.service.dispatch;

import com.siyu.fleet_mgmt_sys.dto.dispatch.DispatchDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.KeyLocations;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.DispatchPhase;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.repository.WayPointRepository;
import com.siyu.fleet_mgmt_sys.service.RobotRepairService;
import com.siyu.fleet_mgmt_sys.service.WebsocketPublisherService;
import com.siyu.fleet_mgmt_sys.service.robot.RobotService;
import com.siyu.fleet_mgmt_sys.service.route.RouteService;
import com.siyu.fleet_mgmt_sys.service.task.TaskService;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
public class DispatchService {

    private final TaskAllocationService allocationService;
    private final RouteService routeService;
    private final WebsocketPublisherService publisher;
    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final RobotService robotService;
    private final TaskService taskService;
    private final RobotRepairService robotRepairService;
    private final WayPointRepository wayPointRepository;

    private final Map<Long, DispatchDTO> dispatches = new ConcurrentHashMap<>();

    public DispatchService(TaskAllocationService allocationService,
            RouteService routeService,
            WebsocketPublisherService publisher,
            RobotRepository robotRepository,
            TaskRepository taskRepository,
            RobotService robotService,
            TaskService taskService,
            @Lazy RobotRepairService robotRepairService,
            WayPointRepository wayPointRepository) {
        this.allocationService = allocationService;
        this.routeService = routeService;
        this.publisher = publisher;
        this.robotRepository = robotRepository;
        this.taskRepository = taskRepository;
        this.robotService = robotService;
        this.taskService = taskService;
        this.robotRepairService = robotRepairService;
        this.wayPointRepository = wayPointRepository;
    }

    @Transactional
    public void allocateAndDispatch(Long simulationId) {
        for (Robot robot : allocationService.allocate(simulationId)) {
            dispatchToTaskStart(robot);
        }
    }

    @Transactional
    public void onArrive(Long robotId, long revision) {
        DispatchDTO current = dispatches.get(robotId);
        if (current == null || current.getRevision() != revision) {
            log.debug("Ignoring stale arrival robot={} rev={}", robotId, revision);
            return;
        }
        switch (current.getPhase()) {
            case TO_TASK_START -> beginExecute(robotId, current.getTaskId());
            case EXECUTE_TASK -> completeAndContinue(robotId, current.getTaskId(), current.getSimulationId());
            case TO_BASE -> arriveAtBase(robotId, current.getSimulationId());

            case BEING_TOWED -> dispatches.remove(robotId);
            case IDLE -> {
                /* nothing to advance */ }
        }
    }

    public List<DispatchDTO> snapshot(Long simulationId) {
        return dispatches.values().stream()
                .filter(d -> Objects.equals(d.getSimulationId(), simulationId))
                .toList();
    }

    public void clear(Long simulationId) {
        dispatches.values().removeIf(d -> Objects.equals(d.getSimulationId(), simulationId));
    }

    public void clearAll() {
        dispatches.clear();
    }

    public void cancelDispatch(Long robotId) {
        dispatches.remove(robotId);
    }

    @Transactional
    public void sendToBaseIfIdle(Long robotId) {
        Robot robot = reload(robotId);
        if (robot.getCurrentTask() == null) {
            dispatchToBase(robot);
        }
    }

    /** Sends an idle repaired robot back to base. */
    @Transactional
    public void userSendToBase(Long robotId) {
        userDirectedTravel(robotId, "UserReq - Send To Base",
                robot -> new WayPoint(robot.getBaseLatitude(), robot.getBaseLongitude()), false);
    }

    @Transactional
    public void userSendToServicing(Long robotId) {
        userDirectedTravel(robotId, "UserReq - Send to Servicing",
                robot -> new WayPoint(KeyLocations.repairLatitude, KeyLocations.repairLongitude), true);
    }

    private void userDirectedTravel(Long robotId, String taskName, Function<Robot, WayPoint> destination,
            boolean triggersMaintenance) {
        Robot robot = reload(robotId);

        if (robot.getStatus() == RobotStatus.UNDER_MAINTENANCE || robot.getStatus() == RobotStatus.NEED_MAINTENANCE) {
            throw new IllegalArgumentException(
                    "Robot " + robot.getName() + " is currently " + robot.getStatus()
                            + " and cannot be redirected until that resolves.");
        }

        Long simulationId = robot.getSimulationId();

        // Stop any in-flight leg so no stale arrival can advance the old task after
        // we've dropped it.
        cancelDispatch(robotId);

        // Drop current task(s) back to the common pool for another robot to pick up.
        List<Task> dropped = new ArrayList<>(robot.getTasks());
        for (Task task : dropped) {
            task.setRobot(null);
            task.setStatus(TaskStatus.PENDING_ASSIGNMENT);
            taskRepository.save(task);
        }
        robot.getTasks().clear();
        log.info("{}: robot {} dropped {} task(s) back to the pool", taskName, robot.getName(), dropped.size());

        WayPoint start = wayPointRepository.save(new WayPoint(robot.getLatitude(), robot.getLongitude()));
        WayPoint end = wayPointRepository.save(destination.apply(robot));

        Task task = new Task();
        task.setName(taskName);
        task.setDescription(taskName + " for " + robot.getName());
        task.setPriority(1);
        task.setStartWayPoint(start);
        task.setEndWayPoint(end);
        task.setStartDateTime(LocalDateTime.now());
        task.setCompletionDateTime(LocalDateTime.now().plusHours(2));
        task.setType(robot.getType() == RobotType.LARGE ? TaskType.LARGE : TaskType.STANDARD);
        task.setTaskDuration(0.0);
        if (triggersMaintenance) {
            task.setTargetRobotId(robot.getId());
        }
        if (simulationId != null) {
            task.setSimulated(true);
            task.setSimulationId(simulationId);
        }
        task.setRoute(routeService.getRouteForTask(task));
        Task savedTask = taskRepository.save(task);

        // Direct assignment (not the pool matcher) - guarantees this robot, not just
        // any eligible one.
        allocationService.assign(robot, savedTask, false);

        dispatchToTaskStart(robot);
    }

    private void dispatchToTaskStart(Robot robot) {
        Task task = robot.getCurrentTask();
        if (task == null)
            return;
        WayPoint from = new WayPoint(robot.getLatitude(), robot.getLongitude());
        DispatchDTO leg = buildLeg(robot, task, DispatchPhase.TO_TASK_START, from, task.getStartWayPoint());
        publish(leg);

        // routing failed entirely, then release the pairing instead of leaving the
        // robot stuck
        if (leg.isBlocked()) {
            releaseUnroutableAssignment(robot, task);
        }
    }

    private void releaseUnroutableAssignment(Robot robot, Task task) {
        log.warn("Routing permanently failed for robot={} task={} - releasing back to the pool for retry",
                robot.getName(), task.getId());
        task.setRobot(null);
        task.setStatus(TaskStatus.PENDING_ASSIGNMENT);
        taskRepository.save(task);

        robot.getTasks().remove(task);
        robot.setStatus(RobotStatus.IDLE);
        robotRepository.save(robot);

        dispatches.remove(robot.getId());
    }

    private void beginExecute(Long robotId, Long taskId) {
        Robot robot = reload(robotId);
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null)
            return;
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
        DispatchDTO leg = legFromRoute(robot, task, DispatchPhase.EXECUTE_TASK, task.getRoute(), task.getEndWayPoint());
        publish(leg);

        boolean hasSeparatePassenger = task.getTargetRobotId() != null && !task.getTargetRobotId().equals(robotId);
        if (hasSeparatePassenger && !leg.isBlocked()) {
            publishTowShadow(robot, task, leg);
        }
    }

    private void publishTowShadow(Robot towRobot, Task breakdownTask, DispatchDTO towLeg) {
        Robot brokenRobot = robotRepository.findById(breakdownTask.getTargetRobotId()).orElse(null);
        if (brokenRobot == null)
            return;

        log.info("Robot {} is towing robot {} to repair", towRobot.getName(), brokenRobot.getName());

        DispatchDTO shadow = DispatchDTO.builder()
                .simulationId(towLeg.getSimulationId())
                .robotId(brokenRobot.getId())
                .revision(nextRevision(brokenRobot.getId()))
                .taskId(breakdownTask.getId())
                .phase(DispatchPhase.BEING_TOWED)
                .routeGeo(towLeg.getRouteGeo())
                .distanceM(towLeg.getDistanceM())
                .etaSeconds(towLeg.getEtaSeconds())
                .destLat(towLeg.getDestLat())
                .destLng(towLeg.getDestLng())
                .blocked(false)
                .build();
        publish(shadow);
    }

    private void completeAndContinue(Long robotId, Long taskId, Long simulationId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        Long targetRobotId = task != null ? task.getTargetRobotId() : null;
        // self-directed servicing the robot that ran the task is also the one needing
        // repai
        boolean selfRepair = targetRobotId != null && targetRobotId.equals(robotId);

        Robot brokenRobot = null;
        if (targetRobotId != null) {
            brokenRobot = robotRepository.findById(targetRobotId).orElse(null);
            if (brokenRobot != null) {
                brokenRobot.setLatitude(task.getEndWayPoint().getLatitude());
                brokenRobot.setLongitude(task.getEndWayPoint().getLongitude());
                robotRepository.save(brokenRobot);
            }
        }

        taskService.completeTask(taskId);

        // repair has to start after completeTask, not before - otherwise completeTask's
        // own
        // "robot IDLE" write would stomp the UNDER_MAINTENANCE status we're about to
        // set
        if (targetRobotId != null) {
            try {
                if (selfRepair) {
                    if (brokenRobot != null) {
                        robotRepairService.startSelfRepair(brokenRobot);
                    }
                } else {
                    Robot towRobot = robotRepository.findById(robotId).orElse(null);
                    if (towRobot != null) {
                        robotRepairService.startRepair(task, towRobot);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to start repair after task {} completed: {}",
                        taskId, e.getMessage(), e);
            }
        }

        allocateAndDispatch(simulationId); // may hand this or another robot its next task

        Robot robot = reload(robotId);
        // skip if it just went into maintenance above - it stays put until repair
        // finishes
        if (robot.getCurrentTask() == null && robot.getStatus() != RobotStatus.UNDER_MAINTENANCE) {
            dispatchToBase(robot); // nothing queued, so head home
        }
    }

    private void arriveAtBase(Long robotId, Long simulationId) {
        robotService.setToBase(robotId); // pure: exact base coords + IDLE
        allocateAndDispatch(simulationId); // a task may have appeared while returning
        Robot robot = reload(robotId);
        if (robot.getCurrentTask() == null) {
            publish(idleDispatch(robot)); // parked idle at base
        }
    }

    private void dispatchToBase(Robot robot) {
        robot.setStatus(RobotStatus.MOVING_TO_BASE);
        robotRepository.save(robot);
        WayPoint from = new WayPoint(robot.getLatitude(), robot.getLongitude());
        WayPoint to = new WayPoint(robot.getBaseLatitude(), robot.getBaseLongitude());
        publish(buildLeg(robot, null, DispatchPhase.TO_BASE, from, to));
    }

    private DispatchDTO buildLeg(Robot robot, Task task, DispatchPhase phase, WayPoint from, WayPoint to) {
        // 1) graph A* router (transient - never persisted)
        try {
            return legFromRoute(robot, task, phase, routeService.getRoute(from, to), to);
        } catch (Exception graphErr) {
            log.warn("Graph routing failed robot={} phase={}: {} - trying OneMap",
                    robot.getName(), phase, graphErr.getMessage());
        }
        // 2) OneMap fallback (external drive route; robot-paced ETA for consistency)
        try {
            OneMapRouteResponseDTO om = routeService.getOneMapRoute(from.toString(), to.toString());
            if (om != null && om.getRouteGeometry() != null) {
                Integer dist = om.getRouteSummary() != null ? om.getRouteSummary().getTotalDistance() : null;
                return base(robot, task, phase)
                        .routeGeo(om.getRouteGeometry())
                        .distanceM(dist)
                        .etaSeconds(paceBySpeed(dist, robot))
                        .destLat(to.getLatitude()).destLng(to.getLongitude())
                        .blocked(false)
                        .build();
            }
        } catch (Exception oneMapErr) {
            log.warn("OneMap routing also failed robot={} phase={}: {}",
                    robot.getName(), phase, oneMapErr.getMessage());
        }
        // 3) blocked - hold position, let the user know
        log.warn("Routing blocked for robot={} phase={}", robot.getName(), phase);
        return base(robot, task, phase).blocked(true)
                .destLat(to.getLatitude()).destLng(to.getLongitude()).build();
    }

    private DispatchDTO legFromRoute(Robot robot, Task task, DispatchPhase phase, Route route, WayPoint to) {
        if (route == null) { // missing task route - rebuild start to end
            return buildLeg(robot, task, phase, task.getStartWayPoint(), task.getEndWayPoint());
        }
        return base(robot, task, phase)
                .routeGeo(route.getRouteGeo())
                .distanceM(route.getTotalDistance())
                .etaSeconds(etaSeconds(route, robot))
                .destLat(to.getLatitude()).destLng(to.getLongitude())
                .blocked(false)
                .build();
    }

    private DispatchDTO idleDispatch(Robot robot) {
        return base(robot, null, DispatchPhase.IDLE)
                .destLat(robot.getBaseLatitude()).destLng(robot.getBaseLongitude())
                .build();
    }

    private DispatchDTO.DispatchDTOBuilder base(Robot robot, Task task, DispatchPhase phase) {
        return DispatchDTO.builder()
                .simulationId(robot.getSimulationId())
                .robotId(robot.getId())
                .revision(nextRevision(robot.getId()))
                .taskId(task == null ? null : task.getId())
                .phase(phase);
    }

    private void publish(DispatchDTO dispatch) {
        dispatches.put(dispatch.getRobotId(), dispatch);
        publisher.publishDispatch(dispatch.getSimulationId(), dispatch);
    }

    private long nextRevision(Long robotId) {
        DispatchDTO existing = dispatches.get(robotId);
        return existing == null ? 1 : existing.getRevision() + 1;
    }

    private double etaSeconds(Route route, Robot robot) {
        Double eta = route.getEstimatedTimeFor(robot.getType());
        if (eta != null && eta > 0)
            return eta;
        return paceBySpeed(route.getTotalDistance(), robot);
    }

    // sim-seconds to cover `distanceM` at the robot's speed (fallback nominal speed
    // / floor)
    private double paceBySpeed(Integer distanceM, Robot robot) {
        double speed = robot.getSpeed() > 0 ? robot.getSpeed() : 1.5; // fallback m/s
        int dist = distanceM != null ? distanceM : 0;
        return Math.max(dist / speed, 1.0);
    }

    private Robot reload(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
        robot.getTasks().size(); // init lazy tasks within the txn
        return robot;
    }
}

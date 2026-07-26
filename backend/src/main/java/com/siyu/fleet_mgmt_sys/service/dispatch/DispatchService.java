package com.siyu.fleet_mgmt_sys.service.dispatch;

import com.siyu.fleet_mgmt_sys.dto.dispatch.DispatchDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.DispatchPhase;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.WebsocketPublisherService;
import com.siyu.fleet_mgmt_sys.service.robot.RobotService;
import com.siyu.fleet_mgmt_sys.service.route.RouteService;
import com.siyu.fleet_mgmt_sys.service.task.TaskService;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend-authoritative movement. Owns each robot's current leg (a {@link DispatchDTO}) in an
 * in-memory map, drives the lifecycle base → task start → execute → next task | base, and pushes
 * every leg to the frontend, which animates it and reports arrival. A monotonic revision per
 * robot supersedes a return trip when the robot is re-tasked mid-way and drops stale arrivals.
 *
 * ponytail: in-memory state (no DB table) — a mid-run backend restart loses dispatch state, which
 * is fine for a demo sim (the frontend resets). Add persistence only if durability is needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final TaskAllocationService allocationService;
    private final RouteService routeService;
    private final WebsocketPublisherService publisher;
    private final RobotRepository robotRepository;
    private final TaskRepository taskRepository;
    private final RobotService robotService;
    private final TaskService taskService;

    private final Map<Long, DispatchDTO> dispatches = new ConcurrentHashMap<>();

    // ── Public API ───────────────────────────────────────────────────────────

    /** Allocate the run's pending pool and push a TO_TASK_START leg to each newly-assigned robot. */
    @Transactional
    public void allocateAndDispatch(Long simulationId) {
        for (Robot robot : allocationService.allocate(simulationId)) {
            dispatchToTaskStart(robot);
        }
    }

    /** Frontend reports a robot finished the leg it was animating. Ignored if the revision is stale. */
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
            case IDLE -> { /* nothing to advance */ }
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

    /** Drop a robot's dispatch (e.g. on breakdown) so no stale arrival can advance it. */
    public void cancelDispatch(Long robotId) {
        dispatches.remove(robotId);
    }

    // ── State transitions ────────────────────────────────────────────────────

    private void dispatchToTaskStart(Robot robot) {
        Task task = robot.getCurrentTask();
        if (task == null) return;
        WayPoint from = new WayPoint(robot.getLatitude(), robot.getLongitude());
        publish(buildLeg(robot, task, DispatchPhase.TO_TASK_START, from, task.getStartWayPoint()));
    }

    private void beginExecute(Long robotId, Long taskId) {
        Robot robot = reload(robotId);
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
        // reuse the task's own start→end route (built at submission)
        publish(legFromRoute(robot, task, DispatchPhase.EXECUTE_TASK, task.getRoute(), task.getEndWayPoint()));
    }

    private void completeAndContinue(Long robotId, Long taskId, Long simulationId) {
        taskService.completeTask(taskId);       // pure: COMPLETED, unlink, release deps, robot IDLE
        allocateAndDispatch(simulationId);       // may hand this or another robot its next task
        Robot robot = reload(robotId);
        if (robot.getCurrentTask() == null) {
            dispatchToBase(robot);               // nothing queued → head home
        }
        // else allocateAndDispatch already pushed this robot a direct TO_TASK_START
    }

    private void arriveAtBase(Long robotId, Long simulationId) {
        robotService.setToBase(robotId);         // pure: exact base coords + IDLE
        allocateAndDispatch(simulationId);       // a task may have appeared while returning
        Robot robot = reload(robotId);
        if (robot.getCurrentTask() == null) {
            publish(idleDispatch(robot));        // parked idle at base
        }
    }

    private void dispatchToBase(Robot robot) {
        robot.setStatus(RobotStatus.MOVING_TO_BASE);
        robotRepository.save(robot);
        WayPoint from = new WayPoint(robot.getLatitude(), robot.getLongitude());
        WayPoint to = new WayPoint(robot.getBaseLatitude(), robot.getBaseLongitude());
        publish(buildLeg(robot, null, DispatchPhase.TO_BASE, from, to));
    }

    // ── Leg builders ─────────────────────────────────────────────────────────

    private DispatchDTO buildLeg(Robot robot, Task task, DispatchPhase phase, WayPoint from, WayPoint to) {
        // 1) graph A* router (transient — never persisted)
        try {
            return legFromRoute(robot, task, phase, routeService.getRoute(from, to), to);
        } catch (Exception graphErr) {
            log.warn("Graph routing failed robot={} phase={}: {} — trying OneMap",
                    robot.getId(), phase, graphErr.getMessage());
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
                    robot.getId(), phase, oneMapErr.getMessage());
        }
        // 3) blocked — hold position, let the user know
        log.warn("Routing blocked for robot={} phase={}", robot.getId(), phase);
        return base(robot, task, phase).blocked(true)
                .destLat(to.getLatitude()).destLng(to.getLongitude()).build();
    }

    private DispatchDTO legFromRoute(Robot robot, Task task, DispatchPhase phase, Route route, WayPoint to) {
        if (route == null) {   // missing task route — rebuild start→end
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

    // ── Helpers ──────────────────────────────────────────────────────────────

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
        if (eta != null && eta > 0) return eta;
        return paceBySpeed(route.getTotalDistance(), robot);
    }

    // sim-seconds to cover `distanceM` at the robot's speed (fallback nominal speed / floor)
    private double paceBySpeed(Integer distanceM, Robot robot) {
        double speed = robot.getSpeed() > 0 ? robot.getSpeed() : 1.5;   // fallback m/s
        int dist = distanceM != null ? distanceM : 0;
        return Math.max(dist / speed, 1.0);
    }

    private Robot reload(Long robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new RobotNotFoundException(robotId));
        robot.getTasks().size();  // init lazy tasks within the txn
        return robot;
    }
}

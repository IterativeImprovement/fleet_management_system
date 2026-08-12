package com.siyu.fleet_mgmt_sys.service.task.allocation;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backend-authoritative allocation. Scans the PENDING_ASSIGNMENT pool for a run
 * and matches each
 * task to the nearest eligible free robot of the same run. Eligibility (robot
 * type + deadline
 * feasibility) is precomputed at submission by TaskPriorityService - a priority
 * of -1 means the
 * robot cannot do the task. Returns the robots that were newly assigned so the
 * caller
 * (DispatchService) can create/redirect their dispatch legs.
 *
 * relies on serial simulation triggers (no DB locking); one client drives
 * events in
 * order, so double-assignment can't race here. Add pessimistic locking only if
 * that changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAllocationService {

    private final TaskRepository taskRepository;
    private final RobotRepository robotRepository;

    /**
     * Links a robot to a task and marks both ASSIGNED. Used by allocate() and robot
     * creation.
     */
    public void assign(Robot robot, Task task, boolean isAlreadyInTaskList) {
        task.setStatus(TaskStatus.ASSIGNED);
        task.setRobot(robot);
        robot.setStatus(RobotStatus.ASSIGNED);
        if (!isAlreadyInTaskList) {
            robot.getTasks().add(task);
        }
        taskRepository.save(task);
        robotRepository.save(robot);
    }

    /**
     * Match pending tasks (for the given run, or live when simulationId is null) to
     * the nearest
     * eligible free robot. Returns the robots that received a new task.
     */
    @Transactional
    public List<Robot> allocate(Long simulationId) {
        // copy - we sort in place, and the repository/List.of source may be immutable
        List<Task> pending = new ArrayList<>(simulationId != null
                ? taskRepository.findByStatusAndSimulationId(TaskStatus.PENDING_ASSIGNMENT, simulationId)
                : taskRepository.findByStatusAndSimulationIdIsNull(TaskStatus.PENDING_ASSIGNMENT));
        if (pending.isEmpty())
            return List.of();

        List<Robot> free = freeRobots(simulationId);
        if (free.isEmpty())
            return List.of();

        // higher user priority (lower number) first, then earlier deadline
        pending.sort(Comparator.comparingInt(Task::getPriority)
                .thenComparing(Task::getCompletionDateTime, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Robot> assigned = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        for (Task task : pending) {
            Robot best = null;
            double bestDist = Double.MAX_VALUE;
            for (Robot robot : free) {
                if (used.contains(robot.getId()))
                    continue;
                double priority = task.getPriorityFor(robot.getType());
                if (priority < 0)
                    continue; // ineligible: type or deadline
                double d = distanceTo(robot, task);
                if (d < bestDist || (d == bestDist && (best == null || robot.getId() < best.getId()))) {
                    best = robot;
                    bestDist = d;
                }
            }
            if (best != null) {
                assign(best, task, false);
                used.add(best.getId());
                assigned.add(best);
            }
        }

        log.info("Allocating: {} pending tasks, {} free robots", pending.size(), free.size());
        for (Task t : pending) {
            log.info("  Pending task: {} type={} priorities={}", t.getName(), t.getType(), t.getCalculatedPriorities());
        }
        for (Robot r : free) {
            log.info("  Free robot: {} type={} status={} currentTask={}",
                    r.getName(), r.getType(), r.getStatus(), r.getCurrentTask());
        }

        return assigned;

    }

    private List<Robot> freeRobots(Long simulationId) {
        List<Robot> robots = simulationId != null
                ? robotRepository.findBySimulatedTrueAndSimulationId(simulationId)
                : robotRepository.findBySimulatedFalse();
        return robots.stream()
                .filter(r -> r.getStatus() == RobotStatus.IDLE || r.getStatus() == RobotStatus.MOVING_TO_BASE)
                .filter(r -> r.getCurrentTask() == null)
                .toList();
    }

    // straight-line lat/lng distance from a robot's current position to the task
    // start
    private double distanceTo(Robot robot, Task task) {
        double dLat = robot.getLatitude() - task.getStartWayPoint().getLatitude();
        double dLng = robot.getLongitude() - task.getStartWayPoint().getLongitude();
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }
}

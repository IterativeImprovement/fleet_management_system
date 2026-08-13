package com.siyu.fleet_mgmt_sys.service.task.allocation;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.graph.GraphBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAllocationService {

    private final TaskRepository taskRepository;
    private final RobotRepository robotRepository;

    @Value("${allocation.weight.time:0.7}")
    private double timeWeight = 0.7;

    @Value("${allocation.weight.priority:0.3}")
    private double priorityWeight = 0.3;

    @Value("${task.priority.smallest:1}")
    private int smallestPriority = 1;

    @Value("${task.priority.largest:5}")
    private int largestPriority = 5;

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
        List<Task> expired = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (Task task : pending) {
            double remainingSeconds = remainingSeconds(task, now);
            if (remainingSeconds <= 0) {
                expired.add(task);
                continue;
            }

            Robot best = null;
            double bestScore = Double.MAX_VALUE;
            for (Robot robot : free) {
                if (used.contains(robot.getId()))
                    continue;
                if (!isTypeCompatible(task, robot.getType()))
                    continue;

                double score = score(robot, task, remainingSeconds);
                if (Double.isNaN(score))
                    continue;
                if (score < bestScore || (score == bestScore && (best == null || robot.getId() < best.getId()))) {
                    best = robot;
                    bestScore = score;
                }
            }
            if (best != null) {
                assign(best, task, false);
                used.add(best.getId());
                assigned.add(best);
            }
        }

        expireTasks(expired);

        log.info("Allocating: {} pending tasks, {} free robots", pending.size(), free.size());
        for (Task t : pending) {
            log.info("  Pending task: {} type={} priority={} deadline={}",
                    t.getName(), t.getType(), t.getPriority(), t.getCompletionDateTime());
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

    private double score(Robot robot, Task task, double remainingSeconds) {
        double executionSeconds = executionSeconds(task, robot.getType());
        if (Double.isNaN(executionSeconds))
            return Double.NaN;

        double completionRatio =
                (approachSeconds(robot, task) + executionSeconds) / remainingSeconds;
        if (completionRatio > 1)
            return Double.NaN;

        return timeWeight * completionRatio
                + priorityWeight * (1 - normalisePriority(task.getPriority()));
    }

    private double approachSeconds(Robot robot, Task task) {
        double metres = GraphBuilderService.haversineMetres(
                robot.getLatitude(), robot.getLongitude(),
                task.getStartWayPoint().getLatitude(), task.getStartWayPoint().getLongitude());
        double speed = robot.getSpeed();
        return speed > 0 ? metres / speed : Double.MAX_VALUE;
    }

    private double executionSeconds(Task task, RobotType robotType) {
        Route route = task.getRoute();
        if (route == null || route.getEstimatedTimes() == null)
            return Double.NaN;
        Double estimate = route.getEstimatedTimes().get(robotType);
        return estimate == null ? Double.NaN : estimate;
    }

    private double remainingSeconds(Task task, LocalDateTime now) {
        if (task.getCompletionDateTime() == null)
            return Double.MAX_VALUE;
        return Duration.between(now, task.getCompletionDateTime()).toSeconds();
    }

    private boolean isTypeCompatible(Task task, RobotType robotType) {
        return !(task.getType() == TaskType.LARGE && robotType == RobotType.STANDARD);
    }

    private double normalisePriority(int priority) {
        int range = largestPriority - smallestPriority;
        if (range <= 0) return 1.0;
        double normalised = (double) (largestPriority - priority) / range;
        return Math.max(0.0, Math.min(1.0, normalised));
    }

    private void expireTasks(List<Task> expired) {
        for (Task task : expired) {
            task.setStatus(TaskStatus.EXPIRED);
            taskRepository.save(task);
            log.warn("Task {} expired: deadline {} has passed with no assignment",
                    task.getName(), task.getCompletionDateTime());
        }
    }
}

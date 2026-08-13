package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPriorityServiceTest {

    private final TaskPriorityService service = new TaskPriorityService();

    @Test
    void largeTaskIsUnsuitableForStandardRobot() {
        // A LARGE task can never go on a STANDARD robot -> score -1, regardless of timing.
        Task task = task(2, TaskType.LARGE, 10_000, times(500, 1000));

        assertEquals(-1.0, service.calculatePriorities(task).get(RobotType.STANDARD), 0.0);
    }

    @Test
    void taskThatCannotFinishInTimeIsRejected() {
        // Estimated 1000s but only 100s until the deadline -> tightness 10 (>1) -> -1.
        Task task = task(2, TaskType.STANDARD, 100, times(1000, 1000));

        assertEquals(-1.0, service.calculatePriorities(task).get(RobotType.LARGE), 0.0);
    }

    @Test
    void higherUserPriorityYieldsHigherScore() {
        // priority 1 (most urgent) should out-score priority 3, all else equal.
        Task urgent   = task(1, TaskType.STANDARD, 10_000, times(100, 100));
        Task relaxed  = task(3, TaskType.STANDARD, 10_000, times(100, 100));

        double urgentScore  = service.calculatePriorities(urgent).get(RobotType.LARGE);
        double relaxedScore = service.calculatePriorities(relaxed).get(RobotType.LARGE);

        assertTrue(urgentScore > relaxedScore,
                "urgent=" + urgentScore + " should exceed relaxed=" + relaxedScore);
    }

    @Test
    void weightingMathProducesExpectedScore() {
        // priority 2 of range 1-5 -> userPriorityScore 0.75; est 1000s of ~10000s -> tightness ~0.1
        // score = 0.1*0.2 + 0.75*0.8 = 0.62
        Task task = task(2, TaskType.STANDARD, 10_000, times(1000, 1000));

        assertEquals(0.62, service.calculatePriorities(task).get(RobotType.LARGE), 0.01);
    }


    private static Map<RobotType, Double> times(double standardSeconds, double largeSeconds) {
        return Map.of(RobotType.STANDARD, standardSeconds, RobotType.LARGE, largeSeconds);
    }

    private static Task task(int priority, TaskType type, long secondsUntilDeadline,
                             Map<RobotType, Double> estimatedTimes) {
        Route route = new Route();
        route.setEstimatedTimes(estimatedTimes);

        Task task = new Task();
        task.setPriority(priority);
        task.setType(type);
        task.setCompletionDateTime(LocalDateTime.now().plusSeconds(secondsUntilDeadline));
        task.setRoute(route);
        return task;
    }
}

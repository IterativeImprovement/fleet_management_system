package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/* This service calculates a weighted priority of tasks for each robot based on user priority, urgency / tightness, and suitability of robot for task.
 *
 * A robot which cannot complete a task for whatever reason will have a priority of -1, which means it will get passed over during task allocation.
 * */

@Slf4j
@Service
@NoArgsConstructor
public class TaskPriorityService {

    @Value("${task.priority.smallest:1}")
    private int smallestPriority = 1;

    @Value("${task.priority.largest:5}")
    private int largestPriority = 5;

    public Map<RobotType, Double> calculatePriorities(Task task) {

        // Null guard - must come before any field access
        if (task.getRoute() == null || task.getRoute().getEstimatedTimes() == null) {
            log.warn("Task {} has no route or estimatedTimes - assigning fallback priority", task.getName());
            Map<RobotType, Double> fallback = new HashMap<>();
            for (RobotType robotType : RobotType.values()) {
                if (robotType == RobotType.UNINITIALISED) continue;
                fallback.put(robotType, 0.1); // low but positive - robot can still pick it up
            }
            return fallback;
        }

        double userPriorityScore = normalisePriority(task.getPriority());
        double[] priorityWeights = {0.2, 0.8};

        Duration remainingTime = Duration.between(LocalDateTime.now(), task.getCompletionDateTime());
        Map<RobotType, Double> estimatedTimes = task.getRoute().getEstimatedTimes();
        Map<RobotType, Double> calculatedPriorities = new HashMap<>();

        for (RobotType robotType : RobotType.values()) {
            if (robotType == RobotType.UNINITIALISED) continue;

            // exclude robots that cannot complete this task type
            if (task.getType() == TaskType.LARGE && robotType == RobotType.STANDARD) {
                calculatedPriorities.put(robotType, -1.0);
                continue;
            }

            // exclude if deadline has already passed
            if (remainingTime.isNegative() || remainingTime.isZero()) {
                calculatedPriorities.put(robotType, -1.0);
                continue;
            }

            Double robotEstimate = estimatedTimes.get(robotType);
            if (robotEstimate == null) {
                log.warn("No estimated time for robotType={} on task={} - skipping", robotType, task.getName());
                calculatedPriorities.put(robotType, -1.0);
                continue;
            }

            double tightness = robotEstimate / remainingTime.toSeconds();

            // exclude robots that cannot complete the task on time
            if (tightness > 1) {
                calculatedPriorities.put(robotType, -1.0);
                continue;
            }

            double priority = tightness * priorityWeights[0]
                    + userPriorityScore * priorityWeights[1];
            calculatedPriorities.put(robotType, priority);
        }

        log.info(calculatedPriorities.toString());
        return calculatedPriorities;
    }

    private double normalisePriority(int priority) {
        int range = largestPriority - smallestPriority;
        if (range <= 0) return 1.0;
        double normalised = (double) (largestPriority - priority) / range;
        return Math.max(0.0, Math.min(1.0, normalised));
    }
}
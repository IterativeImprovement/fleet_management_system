package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/* This service calculates a weighted priority of tasks for each robot based on user priority, urgency / tightness, and suitability of robot for task.
*
* A robot which cannot complete a task for whatever reason will have a priority of -1, which means it will get passed over during task allocation.
* */

@Service
@NoArgsConstructor
public class TaskPriorityService {
    public Map<RobotType, Double> calculatePriorities(Task task) {
        double userPriorityScore = 4 - task.getPriority();
        double[] priorityWeights = {0.2, 0.8}; // how userPriority weights compared to calculated priorities

        // tightness - how estimated time to completion compares to the latest completion deadline
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

            double tightness = estimatedTimes.get(robotType) / remainingTime.toSeconds();

            // exclude robots that cannot complete the task on time
            if (tightness > 1) {
                calculatedPriorities.put(robotType, -1.0);
                continue;
            }

            double priority = tightness * priorityWeights[0]
                    + userPriorityScore * priorityWeights[1];
            calculatedPriorities.put(robotType, priority);
        }

        return calculatedPriorities;
    }
}

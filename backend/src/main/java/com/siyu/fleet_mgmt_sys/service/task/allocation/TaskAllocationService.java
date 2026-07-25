package com.siyu.fleet_mgmt_sys.service.task.allocation;

import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Objects;

/*
TODO: Dependencies
A task that is dependent on another will not be included in the active task pool until the task it is dependent on has completed.
 */


@Service
@RequiredArgsConstructor
public class TaskAllocationService {

    private final TaskRepository taskRepository;
    private final RobotRepository robotRepository;

    private final TaskClusterService clusterService;

    public void assignNextTask(Robot robot) {
        Cluster robotCluster = robot.getCurrentCluster();

        // robot is not currently in any cluster (e.g. idle at base) - nothing to assign
        if (robotCluster == null) {
            return;
        }

        // step 1: look for the top task in the local cluster first
        Task bestTask = getTopTaskForRobot(robot, robotCluster);

        // step 2: if nothing local, scan adjacent clusters for their top tasks
        if (bestTask == null) {
            bestTask = robotCluster.getAdjacentClusters().stream()
                    .map(c -> getTopTaskForRobot(robot, c))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(t -> t.getPriorityFor(robot.getType())))
                    .orElse(null);
        }

        if (bestTask != null) {
            assign(robot, bestTask, false);
            clusterService.refreshTopTasks(bestTask.getStartCluster().getId());
        }
        // else robot stays idle, will retry when a new task is submitted
    }

    private Task getTopTaskForRobot(Robot robot, Cluster cluster) {
        return robot instanceof LargeRobot
                ? cluster.getTopLargeTask()
                : cluster.getTopStandardTask();
    }

    public void assign(Robot robot, Task task, boolean isAlreadyInTaskList) {
        task.setStatus(TaskStatus.ASSIGNED); // assigned
        task.setRobot(robot);
        robot.setStatus(RobotStatus.ASSIGNED); // assigned
        if (!isAlreadyInTaskList) {
            robot.getTasks().add(task);
        }
        taskRepository.save(task);
        robotRepository.save(robot);
    }

}
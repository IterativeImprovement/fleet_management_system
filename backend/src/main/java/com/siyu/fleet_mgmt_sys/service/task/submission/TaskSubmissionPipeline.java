package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.route.RouteEstimationService;
import com.siyu.fleet_mgmt_sys.service.route.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskSubmissionPipeline {

    private final TaskRepository taskRepository;

    private final RouteService routeService;
    private final RouteEstimationService estimationService;
    private final TaskPriorityService priorityService;
    private final TaskClusterService clusterService;

    public Task submitTask(Task task) {
        // retrieves route geometry and total distance info
        Route route = routeService.getRoute(task.getStartWayPoint(), task.getEndWayPoint());

        // calculates estimated time to complete routes for different robots
        // 0 is standard, 1 is large
        estimationService.updateRouteEstimatedTimes(route);
        task.setRoute(route);

        // calculates priority of tasks and whether robots can complete said tasks
        Map<RobotType, Double> calculatedPriorities = priorityService.calculatePriorities(task);
        task.setCalculatedPriorities(calculatedPriorities);

        task.setStatus(task.getDependencies().isEmpty()
                ? TaskStatus.PENDING_ASSIGNMENT // if the task has no dependencies, it is immediately added to the pool
                : TaskStatus.WAITING_FOR_DEPENDENCIES); // else, its status is set to waiting

        clusterService.assignCluster(task);
        clusterService.refreshTopTasks(task.getStartCluster());
        clusterService.refreshTopTasks(task.getEndCluster());
        return taskRepository.save(task);
    }

}

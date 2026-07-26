package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.route.RouteService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class TaskSubmissionPipeline {

    private final TaskRepository taskRepository;

    private final RouteService routeService;
    private final TaskPriorityService priorityService;
    private final TaskClusterService clusterService;
    private final DispatchService dispatchService;

    public TaskSubmissionPipeline(
            TaskRepository taskRepository,
            RouteService routeService,
            TaskPriorityService priorityService,
            TaskClusterService clusterService,
            @Lazy DispatchService dispatchService) {
        this.taskRepository = taskRepository;
        this.routeService = routeService;
        this.priorityService = priorityService;
        this.clusterService = clusterService;
        this.dispatchService = dispatchService;
    }


    @Transactional
    public Task submitTask(Task task) {
        // retrieves route info
        Route route = routeService.getRouteForTask(task);
        task.setRoute(route);

        // calculates priority of tasks and whether robots can complete said tasks
        Map<RobotType, Double> calculatedPriorities = priorityService.calculatePriorities(task);
        task.setCalculatedPriorities(calculatedPriorities);

        task.setStatus(task.getDependencies().isEmpty()
                ? TaskStatus.PENDING_ASSIGNMENT // if the task has no dependencies, it is immediately added to the pool
                : TaskStatus.WAITING_FOR_DEPENDENCIES); // else, its status is set to waiting

        clusterService.assignCluster(task);
        Task savedTask = taskRepository.saveAndFlush(task);

        Set<Long> clusterIds = new LinkedHashSet<>();
        if (task.getStartCluster() != null) {
            clusterIds.add(task.getStartCluster().getId());
        }
        if (task.getEndCluster() != null) {
            clusterIds.add(task.getEndCluster().getId());
        }
        clusterIds.forEach(clusterService::refreshTopTasks);

        // trigger allocation and dispatch after task is saved
        if (savedTask.getStatus() == TaskStatus.PENDING_ASSIGNMENT
                && savedTask.getSimulationId() != null) {
            dispatchService.allocateAndDispatch(savedTask.getSimulationId());
        }

        return savedTask;
    }

}

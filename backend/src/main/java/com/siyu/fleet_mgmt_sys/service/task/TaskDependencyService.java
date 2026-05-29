package com.siyu.fleet_mgmt_sys.service.task;

import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TaskDependencyService {
    private final TaskRepository taskRepository;
    private final TaskClusterService clusterService;

    public void releaseUnblockedTasks(Task completedTask) {
        // find all waiting tasks that depend on this completed task
        List<Task> waitingTasks = taskRepository.findByStatusAndDependenciesContaining(
                TaskStatus.WAITING_FOR_DEPENDENCIES, completedTask
        );

        waitingTasks.forEach(t -> {
            if (allDependenciesCompleted(t)) {
                t.setStatus(TaskStatus.PENDING_ASSIGNMENT); // add it to the task pool
                taskRepository.save(t);

                clusterService.refreshTopTasks(t.getStartCluster().getId());
                clusterService.refreshTopTasks(t.getEndCluster().getId());
            }
        });
    }

    private boolean allDependenciesCompleted(Task task) {
        return task.getDependencies().stream()
                .allMatch(d -> d.getStatus() == TaskStatus.COMPLETED);
    }
}

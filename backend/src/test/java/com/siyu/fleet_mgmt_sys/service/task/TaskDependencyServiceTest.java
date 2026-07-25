package com.siyu.fleet_mgmt_sys.service.task;

import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDependencyServiceTest {

    private TaskRepository taskRepository;
    private TaskClusterService clusterService;
    private TaskDependencyService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        clusterService = mock(TaskClusterService.class);
        service = new TaskDependencyService(taskRepository, clusterService);
    }

    @Test
    void taskWithAnIncompleteDependencyStaysWaiting() {
        Task completed = taskWithStatus(TaskStatus.COMPLETED);
        Task stillRunning = taskWithStatus(TaskStatus.IN_PROGRESS);
        Task waiting = waitingTaskDependingOn(completed, stillRunning);
        stubWaitingLookup(completed, waiting);

        service.releaseUnblockedTasks(completed);

        assertEquals(TaskStatus.WAITING_FOR_DEPENDENCIES, waiting.getStatus());
        verify(taskRepository, never()).save(waiting);
    }

    @Test
    void taskBecomesPendingOnceAllDependenciesAreCompleted() {
        Task depA = taskWithStatus(TaskStatus.COMPLETED);
        Task depB = taskWithStatus(TaskStatus.COMPLETED);
        Task waiting = waitingTaskDependingOn(depA, depB);
        stubWaitingLookup(depA, waiting);

        service.releaseUnblockedTasks(depA);

        assertEquals(TaskStatus.PENDING_ASSIGNMENT, waiting.getStatus());  // released into the pool
        verify(taskRepository).save(waiting);
    }

    // --- helpers ---

    private void stubWaitingLookup(Task completedTask, Task... waiting) {
        when(taskRepository.findByStatusAndDependenciesContaining(
                eq(TaskStatus.WAITING_FOR_DEPENDENCIES), any(Task.class)))
                .thenReturn(List.of(waiting));
    }

    private static Task taskWithStatus(TaskStatus status) {
        Task task = new Task();
        task.setStatus(status);
        return task;
    }

    private static Task waitingTaskDependingOn(Task... deps) {
        Task task = taskWithStatus(TaskStatus.WAITING_FOR_DEPENDENCIES);
        task.getDependencies().addAll(List.of(deps));
        task.setStartCluster(cluster(1L));
        task.setEndCluster(cluster(2L));
        return task;
    }

    private static Cluster cluster(long id) {
        Cluster cluster = new Cluster();
        cluster.setId(id);
        return cluster;
    }
}

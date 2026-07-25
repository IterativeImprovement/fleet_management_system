package com.siyu.fleet_mgmt_sys.service.task.submission;

import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.TaskClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskClusterServiceTest {

    private TaskClusterRepository clusterRepository;
    private TaskClusterService service;

    @BeforeEach
    void setUp() {
        clusterRepository = mock(TaskClusterRepository.class);
        service = new TaskClusterService(clusterRepository);
    }

    @Test
    void createsANewClusterWhenNoneExistNearby() {
        when(clusterRepository.findAll()).thenReturn(List.of());
        Task task = taskAt(1.30, 103.85, 1.31, 103.86);

        service.assignCluster(task);

        assertNotNull(task.getStartCluster());
        assertNotNull(task.getEndCluster());
        verify(clusterRepository, atLeastOnce()).save(any(Cluster.class));
    }

    @Test
    void reusesAnExistingClusterWithinThreshold() {
        Cluster existing = new Cluster();
        existing.setId(1L);
        existing.setCentroidLat(1.30);
        existing.setCentroidLng(103.85);
        when(clusterRepository.findAll()).thenReturn(List.of(existing));

        // Both endpoints sit right on the existing centroid -> both should reuse it.
        Task task = taskAt(1.30, 103.85, 1.30, 103.85);

        service.assignCluster(task);

        assertSame(existing, task.getStartCluster());
        assertSame(existing, task.getEndCluster());
        assertTrue(existing.getStartTasks().contains(task));
        assertTrue(existing.getEndTasks().contains(task));
    }

    private static Task taskAt(double startLat, double startLng, double endLat, double endLng) {
        Task task = new Task();
        task.setStartWayPoint(new WayPoint(startLat, startLng));
        task.setEndWayPoint(new WayPoint(endLat, endLng));
        return task;
    }
}

package com.siyu.fleet_mgmt_sys.service.task.allocation;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.LargeRobot;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.submission.TaskClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskAllocationServiceTest {

    private TaskRepository taskRepository;
    private RobotRepository robotRepository;
    private TaskClusterService clusterService;
    private TaskAllocationService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        robotRepository = mock(RobotRepository.class);
        clusterService = mock(TaskClusterService.class);
        service = new TaskAllocationService(taskRepository, robotRepository, clusterService);
    }

    @Test
    void assignWiresUpBothSidesAndPersists() {
        Robot robot = new StandardRobot("R1");
        Task task = new Task();

        service.assign(robot, task, false);

        assertEquals(TaskStatus.ASSIGNED, task.getStatus());
        assertSame(robot, task.getRobot());
        assertEquals(RobotStatus.ASSIGNED, robot.getStatus());
        assertTrue(robot.getTasks().contains(task));
        verify(taskRepository).save(task);
        verify(robotRepository).save(robot);
    }

    @Test
    void standardRobotGetsTheClustersTopStandardTask() {
        Cluster cluster = clusterWithTopTasks();
        Robot robot = new StandardRobot("std");
        robot.setCurrentCluster(cluster);

        service.assignNextTask(robot);

        assertSame(cluster.getTopStandardTask(), robot.getTasks().get(0));
    }

    @Test
    void largeRobotGetsTheClustersTopLargeTask() {
        Cluster cluster = clusterWithTopTasks();
        Robot robot = new LargeRobot("lrg");
        robot.setCurrentCluster(cluster);

        service.assignNextTask(robot);

        assertSame(cluster.getTopLargeTask(), robot.getTasks().get(0));
    }

    // --- helpers ---

    // A cluster (id 1) holding distinct top tasks for each robot type.
    private static Cluster clusterWithTopTasks() {
        Cluster cluster = new Cluster();
        cluster.setId(1L);
        cluster.setTopStandardTask(taskInCluster(cluster));
        cluster.setTopLargeTask(taskInCluster(cluster));
        return cluster;
    }

    private static Task taskInCluster(Cluster cluster) {
        Task task = new Task();
        task.setStartCluster(cluster);   // assignNextTask refreshes this cluster after assigning
        return task;
    }
}

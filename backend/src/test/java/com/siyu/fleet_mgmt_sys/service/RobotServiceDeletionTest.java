package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.exception.RobotHasAssignedTasksException;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.task.allocation.TaskAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotServiceDeletionTest {
    private RobotRepository robotRepository;
    private RobotService robotService;

    @BeforeEach
    void setUp() {
        robotRepository = mock(RobotRepository.class);
        robotService = new RobotService(
                robotRepository,
                mock(TaskRepository.class),
                mock(TaskAllocationService.class)
        );
    }

    @Test
    void deletesRobotWithoutAssignedTasks() {
        Robot robot = new StandardRobot("001");
        when(robotRepository.findById(1L)).thenReturn(Optional.of(robot));

        robotService.deleteRobot(1L);

        verify(robotRepository).delete(robot);
    }

    @Test
    void rejectsRobotWithAssignedTasks() {
        Robot robot = new StandardRobot("001");
        robot.getTasks().add(mock(Task.class));
        when(robotRepository.findById(1L)).thenReturn(Optional.of(robot));

        assertThrows(
                RobotHasAssignedTasksException.class,
                () -> robotService.deleteRobot(1L)
        );

        verify(robotRepository, never()).delete(robot);
    }
}

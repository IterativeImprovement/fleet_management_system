package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.task.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotBreakdownServiceTest {

    private RobotRepository robotRepository;
    private TaskRepository taskRepository;
    private DispatchService dispatchService;
    private RobotBreakdownService service;
    private TaskService taskService;
    private WebsocketPublisherService websocketPublisherService;

    @BeforeEach
    void setUp() {
        robotRepository = mock(RobotRepository.class);
        taskRepository = mock(TaskRepository.class);
        dispatchService = mock(DispatchService.class);
        taskService = mock(TaskService.class);
        websocketPublisherService = mock(WebsocketPublisherService.class);
        service = new RobotBreakdownService(robotRepository, taskRepository, dispatchService, taskService, websocketPublisherService);
    }

    @Test
    void breakdownReturnsTaskToPoolMarksErrorAndReallocates() {
        Robot robot = new StandardRobot("R1");
        robot.setId(1L);
        robot.setSimulated(true);
        robot.setSimulationId(5L);
        robot.setStatus(RobotStatus.ASSIGNED);

        Task task = new Task();
        task.setRobot(robot);
        task.setStatus(TaskStatus.IN_PROGRESS);
        robot.getTasks().add(task);

        when(robotRepository.findById(1L)).thenReturn(Optional.of(robot));

        service.handleRobotBreakdown(1L);

        assertEquals(RobotStatus.NEED_MAINTENANCE, robot.getStatus());
        assertNull(task.getRobot());
        assertEquals(TaskStatus.PENDING_ASSIGNMENT, task.getStatus()); // back in the pool
        assertTrue(robot.getTasks().isEmpty());
        verify(dispatchService).cancelDispatch(1L);
        verify(taskRepository).save(task);
        verify(dispatchService).allocateAndDispatch(5L);               // another robot can pick it up
    }
}

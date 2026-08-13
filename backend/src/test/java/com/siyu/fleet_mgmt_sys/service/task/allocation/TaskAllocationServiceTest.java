package com.siyu.fleet_mgmt_sys.service.task.allocation;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAllocationServiceTest {

    private static final Long SIM = 5L;

    private TaskRepository taskRepository;
    private RobotRepository robotRepository;
    private TaskAllocationService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        robotRepository = mock(RobotRepository.class);
        service = new TaskAllocationService(taskRepository, robotRepository);
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
    void allocatePicksNearestEligibleFreeRobot() {
        Task task = standardTask(1.30, 103.80);
        Robot near = freeRobot(1L, 1.31, 103.80); // closer to the task start
        Robot far = freeRobot(2L, 1.36, 103.80);

        when(taskRepository.findByStatusAndSimulationId(TaskStatus.PENDING_ASSIGNMENT, SIM))
                .thenReturn(List.of(task));
        when(robotRepository.findBySimulatedTrueAndSimulationId(SIM))
                .thenReturn(List.of(near, far));

        List<Robot> assigned = service.allocate(SIM);

        assertEquals(List.of(near), assigned);
        assertTrue(near.getTasks().contains(task));
        assertFalse(far.getTasks().contains(task));
    }

    @Test
    void allocateSkipsIneligibleRobots() {
        Task task = standardTask(1.30, 103.80);
        task.setType(TaskType.LARGE);
        Robot standard = freeRobot(1L, 1.31, 103.80);

        when(taskRepository.findByStatusAndSimulationId(TaskStatus.PENDING_ASSIGNMENT, SIM))
                .thenReturn(List.of(task));
        when(robotRepository.findBySimulatedTrueAndSimulationId(SIM))
                .thenReturn(List.of(standard));

        assertTrue(service.allocate(SIM).isEmpty());
        assertTrue(standard.getTasks().isEmpty());
    }

    @Test
    void allocateSkipsRobotsThatCannotBeatTheDeadline() {
        Task task = standardTask(1.30, 103.80);
        task.setCompletionDateTime(LocalDateTime.now().plusSeconds(30));
        Robot robot = freeRobot(1L, 1.31, 103.80);

        when(taskRepository.findByStatusAndSimulationId(TaskStatus.PENDING_ASSIGNMENT, SIM))
                .thenReturn(List.of(task));
        when(robotRepository.findBySimulatedTrueAndSimulationId(SIM))
                .thenReturn(List.of(robot));

        assertTrue(service.allocate(SIM).isEmpty());
        assertEquals(TaskStatus.PENDING_ASSIGNMENT, task.getStatus());
    }

    @Test
    void allocateMarksTasksPastTheirDeadlineAsExpired() {
        Task task = standardTask(1.30, 103.80);
        task.setCompletionDateTime(LocalDateTime.now().minusMinutes(5));
        Robot robot = freeRobot(1L, 1.31, 103.80);

        when(taskRepository.findByStatusAndSimulationId(TaskStatus.PENDING_ASSIGNMENT, SIM))
                .thenReturn(List.of(task));
        when(robotRepository.findBySimulatedTrueAndSimulationId(SIM))
                .thenReturn(List.of(robot));

        assertTrue(service.allocate(SIM).isEmpty());
        assertEquals(TaskStatus.EXPIRED, task.getStatus());
        verify(taskRepository).save(task);
    }


    private static Task standardTask(double lat, double lng) {
        Route route = new Route();
        route.setEstimatedTimes(Map.of(RobotType.STANDARD, 600.0, RobotType.LARGE, 900.0));

        Task task = new Task();
        task.setPriority(1);
        task.setType(TaskType.STANDARD);
        task.setCompletionDateTime(LocalDateTime.now().plusHours(1));
        task.setRoute(route);
        task.setStartWayPoint(new WayPoint(lat, lng));
        task.setStatus(TaskStatus.PENDING_ASSIGNMENT);
        return task;
    }

    private static Robot freeRobot(long id, double lat, double lng) {
        Robot robot = new StandardRobot("R" + id);
        robot.setId(id);
        robot.setSimulated(true);
        robot.setSimulationId(SIM);
        robot.setPosition(lat, lng);
        return robot;
    }
}

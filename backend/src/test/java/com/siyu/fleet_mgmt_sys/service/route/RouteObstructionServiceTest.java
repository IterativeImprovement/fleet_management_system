package com.siyu.fleet_mgmt_sys.service.route;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.model.robot.StandardRobot;
import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.repository.RouteRepository;
import com.siyu.fleet_mgmt_sys.service.WebsocketPublisherService;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteObstructionServiceTest {

    private static final String BLOCKED_LINK = "L1";

    private RouteGraphService routeGraphService;
    private RouteBuilderService routeBuilderService;
    private RouteRepository routeRepository;
    private RobotRepository robotRepository;
    private WebsocketPublisherService websocketPublisherService;
    private RouteObstructionService service;

    @BeforeEach
    void setUp() {
        routeGraphService = mock(RouteGraphService.class);
        routeBuilderService = mock(RouteBuilderService.class);
        routeRepository = mock(RouteRepository.class);
        robotRepository = mock(RobotRepository.class);
        websocketPublisherService = mock(WebsocketPublisherService.class);
        service = new RouteObstructionService(
                routeGraphService, routeBuilderService,
                routeRepository, robotRepository, websocketPublisherService);
    }

    @Test
    void obstructionReroutesRobotWhoseRoutePassesThroughBlockedLink() {
        Route newRoute = new Route();
        when(routeBuilderService.buildRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(newRoute);
        Robot robot = movingRobotOnLink(BLOCKED_LINK);
        when(robotRepository.findAll()).thenReturn(List.of(robot));

        service.handleObstruction(BLOCKED_LINK);

        verify(routeGraphService).obstructLink(BLOCKED_LINK);       // blocked in the graph
        verify(routeBuilderService).buildRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(routeRepository).save(newRoute);                     // new route persisted
        verify(robotRepository).save(robot);
        verify(websocketPublisherService).publishReroute(robot.getId(), newRoute);
        assertSame(newRoute, robot.getCurrentTask().getRoute());    // robot now follows the detour
    }

    @Test
    void robotNotOnBlockedLinkIsLeftAlone() {
        Robot robot = movingRobotOnLink("L9");   // uses a different road
        when(robotRepository.findAll()).thenReturn(List.of(robot));

        service.handleObstruction(BLOCKED_LINK);

        verify(routeGraphService).obstructLink(BLOCKED_LINK);       // still marks the block
        verify(routeBuilderService, never()).buildRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(websocketPublisherService, never()).publishReroute(anyLong(), any());
    }

    @Test
    void idleRobotOnBlockedLinkIsNotRerouted() {
        Robot robot = movingRobotOnLink(BLOCKED_LINK);
        robot.setStatus(RobotStatus.IDLE);       // not actively moving -> no live route to fix
        when(robotRepository.findAll()).thenReturn(List.of(robot));

        service.handleObstruction(BLOCKED_LINK);

        verify(routeBuilderService, never()).buildRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(websocketPublisherService, never()).publishReroute(anyLong(), any());
    }

    @Test
    void failedRerouteNotifiesFrontendInsteadOfCrashing() {
        when(routeBuilderService.buildRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("no path"));
        Robot robot = movingRobotOnLink(BLOCKED_LINK);
        when(robotRepository.findAll()).thenReturn(List.of(robot));

        service.handleObstruction(BLOCKED_LINK);   // must not throw

        verify(websocketPublisherService).publishRerouteFailed(robot.getId(), BLOCKED_LINK);
        verify(websocketPublisherService, never()).publishReroute(anyLong(), any());
    }



    // An actively-moving robot at a known position, carrying a task whose route runs over `linkId`.
    private static Robot movingRobotOnLink(String linkId) {
        Route route = new Route();
        route.setLinkIds(new java.util.ArrayList<>(List.of(linkId)));

        Task task = new Task();
        task.setRoute(route);
        task.setEndWayPoint(new WayPoint(1.30, 103.85));

        Robot robot = new StandardRobot("R1");
        robot.setId(1L);
        robot.setStatus(RobotStatus.ASSIGNED);
        robot.setPosition(1.26, 103.82);
        robot.getTasks().add(task);
        return robot;
    }
}

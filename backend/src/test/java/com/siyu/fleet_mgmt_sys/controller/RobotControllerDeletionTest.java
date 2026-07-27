package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.exception.GlobalExceptionHandler;
import com.siyu.fleet_mgmt_sys.exception.RobotHasAssignedTasksException;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.robot.RobotMapper;
import com.siyu.fleet_mgmt_sys.service.robot.RobotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RobotControllerDeletionTest {
    private RobotService robotService;
    private RobotMapper robotMapper;
    private DispatchService dispatchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        robotService = mock(RobotService.class);
        robotMapper = mock(RobotMapper.class);
        dispatchService = mock(DispatchService.class);
        mockMvc = standaloneSetup(new RobotController(robotService, robotMapper, dispatchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNoContentWhenRobotIsDeleted() throws Exception {
        mockMvc.perform(delete("/robot/1"))
                .andExpect(status().isNoContent());

        verify(robotService).deleteRobot(1L);
    }

    @Test
    void returnsConflictWhenRobotHasAssignedTasks() throws Exception {
        doThrow(new RobotHasAssignedTasksException(1L))
                .when(robotService).deleteRobot(1L);

        mockMvc.perform(delete("/robot/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete robot 1 with assigned tasks"));
    }
}

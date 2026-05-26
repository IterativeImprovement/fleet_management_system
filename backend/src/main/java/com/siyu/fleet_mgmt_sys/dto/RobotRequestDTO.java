package com.siyu.fleet_mgmt_sys.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotRequestDTO {

    private String name;

    private String type;

    private String status;

    private List<Long> tasksIdsToRemove;

    private List<Long> taskIdsToAdd;
}
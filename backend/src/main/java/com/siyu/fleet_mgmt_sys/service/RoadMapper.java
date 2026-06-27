package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.road.RoadResponseDTO;
import com.siyu.fleet_mgmt_sys.model.Road;
import org.springframework.stereotype.Component;

@Component
public class RoadMapper {

    public RoadResponseDTO toDTO(Road road) {
        if (road == null) {
            return null;
        }

        return RoadResponseDTO.builder()
                .id(road.getId())
                .roadName(road.getRoadName())
                .roadCategory(road.getRoadCategory())
                .startLat(road.getStartLat())
                .startLon(road.getStartLon())
                .endLat(road.getEndLat())
                .endLon(road.getEndLon())
                .status(road.getStatus() != null ? road.getStatus().name() : null)
                .build();
    }
}
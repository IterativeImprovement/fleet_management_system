package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.road.RoadResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RoadNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Road;
import com.siyu.fleet_mgmt_sys.model.enums.RoadStatus;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoadService {
    private final RoadRepository roadRepository;

    private final RoadMapper roadMapper;

    public RoadResponseDTO getRoad(Long id) {
        Road road = roadRepository.findById(id)
                .orElseThrow(() -> new RoadNotFoundException(id));
        return roadMapper.toDTO(road);
    }

    public RoadResponseDTO updateRoadStatus(Long id, String newStatus) {
        Road road = roadRepository.findById(id)
                .orElseThrow(() -> new RoadNotFoundException(id));

        // parse; the body may arrive as a JSON string literal, so strip surrounding quotes
        String parsed = newStatus.strip().replaceAll("^\"|\"$", "");
        if (parsed.equalsIgnoreCase("obstructed")) {
            road.setStatus(RoadStatus.OBSTRUCTED);
        } else if (parsed.equalsIgnoreCase("unobstructed")) {
            road.setStatus(RoadStatus.UNOBSTRUCTED);
        } else {
            throw new IllegalArgumentException();
        }

        roadRepository.save(road);
        return roadMapper.toDTO(road);
    }
}

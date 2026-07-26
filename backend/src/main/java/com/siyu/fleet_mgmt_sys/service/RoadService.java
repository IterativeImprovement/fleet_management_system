package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.road.RoadResponseDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RoadNotFoundException;
import com.siyu.fleet_mgmt_sys.model.Road;
import com.siyu.fleet_mgmt_sys.model.enums.RoadStatus;
import com.siyu.fleet_mgmt_sys.model.graph.GraphEdge;
import com.siyu.fleet_mgmt_sys.repository.RoadRepository;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoadService {
    private final RoadRepository roadRepository;
    private final RoadMapper roadMapper;
    private final RouteGraphService routeGraphService;

    public RoadResponseDTO getRoad(String id) {
        Road road = roadRepository.findById(id)
                .orElseThrow(() -> new RoadNotFoundException(id));
        RoadResponseDTO dto = roadMapper.toDTO(road);
        dto.setSegments(buildSegments(id));
        return dto;
    }

    public RoadResponseDTO updateRoadStatus(String id, String newStatus) {
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
        RoadResponseDTO dto = roadMapper.toDTO(road);
        dto.setSegments(buildSegments(id));
        return dto;
    }

    /**
     * The road's true geometry as drawn in the routing graph — see
     * RouteGraphService.getEdgesByLinkId for why this can be more than one segment.
     */
    private List<List<List<Double>>> buildSegments(String linkId) {
        List<List<List<Double>>> segments = new ArrayList<>();
        for (GraphEdge edge : routeGraphService.getEdgesByLinkId(linkId)) {
            segments.add(List.of(
                    List.of(edge.getFromNode().getLatitude(), edge.getFromNode().getLongitude()),
                    List.of(edge.getToNode().getLatitude(), edge.getToNode().getLongitude())));
        }
        return segments;
    }
}

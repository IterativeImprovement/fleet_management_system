package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.repository.GraphEdgeRepository;
import com.siyu.fleet_mgmt_sys.service.external.LTAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Periodically fetches fresh traffic speed bands from LTA
 * and updates both the in-memory graph and the DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphUpdateService {

    private final LTAService trafficSpeedBandService;
    private final GraphEdgeRepository graphEdgeRepository;
    private final RouteGraphService routeGraphService;

    /**
     * Runs every 5 minutes.
     * Fetches latest speed bands, updates DB and in-memory graph.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000) // updates a minute after initial load, then updates every 5 min
    @Transactional
    public void update() {
        log.info("Updating graph speed bands from LTA...");

        List<LtaTrafficSpeedBandResponseDTO> speedBands = trafficSpeedBandService.getAllSpeedBands();

        int updated = 0;
        for (LtaTrafficSpeedBandResponseDTO dto : speedBands) {
            if (dto.getLinkId() == null || dto.getSpeedBand() <= 0) continue;

            // 1. Get the current speed from memory (assuming you have a getter like this)
            Integer currentSpeed = routeGraphService.getSpeedBand(dto.getLinkId());

            // 2. The Diff Check: Only proceed if it is a new link OR the speed has actually changed
            if (currentSpeed == null || currentSpeed != dto.getSpeedBand()) {

                // Update DB
                graphEdgeRepository.updateSpeedBand(dto.getLinkId(), dto.getSpeedBand());

                // Update in-memory graph
                routeGraphService.updateSpeedBand(dto.getLinkId(), dto.getSpeedBand());

                updated++;
            }
        }

        log.info("Updated speed bands for {} road segments (out of {} checked)", updated, speedBands.size());
    }
}
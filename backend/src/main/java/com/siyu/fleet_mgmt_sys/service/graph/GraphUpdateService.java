package com.siyu.fleet_mgmt_sys.service.graph;

import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.repository.GraphEdgeRepository;
import com.siyu.fleet_mgmt_sys.service.external.LTAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // @ConditionalOnProperty has no effect on a method, so the flag is checked here instead.
    @Value("${lta.sync.enabled:true}")
    private boolean syncEnabled;

    /**
     * Runs 1 minute after startup, then every 100 minutes, when lta.sync.enabled is true.
     * Fetches latest speed bands, updates DB and in-memory graph.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 6_000_000)
    @Transactional
    public void update() {
        if (!syncEnabled) {
            return;
        }
        log.info("Updating graph speed bands from LTA...");

        List<LtaTrafficSpeedBandResponseDTO> speedBands;

        try {
            speedBands = trafficSpeedBandService.getAllSpeedBands();

            // Failsafe: Prevent wiping/ignoring data if the API returns an empty payload
            if (speedBands == null || speedBands.isEmpty()) {
                log.warn("LTA API returned empty dataset. Aborting graph update to protect current state.");
                return;
            }
        } catch (Exception e) {
            // Catch the 500 error / quota violation and abort cleanly
            log.error("Failed to fetch traffic speed bands from LTA: {}. Aborting current sync cycle.", e.getMessage());
            return;
        }

        int updated = 0;
        for (LtaTrafficSpeedBandResponseDTO dto : speedBands) {
            if (dto.getLinkId() == null || dto.getSpeedBand() <= 0) continue;

            // 1. Get the current speed from memory
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
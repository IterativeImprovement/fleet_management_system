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

    @Value("${lta.sync.enabled:true}")
    private boolean syncEnabled = true;

    /**
     * Runs roughly every 100 minutes, starting a minute after boot.
     * Fetches latest speed bands, updates DB and in-memory graph.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 6_000_000)
    @Transactional
    public void update() {
        if (!syncEnabled) {
            log.debug("LTA sync disabled, skipping graph speed band update.");
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

            Integer currentSpeed = routeGraphService.getSpeedBand(dto.getLinkId());

            // diff check
            if (currentSpeed == null || currentSpeed != dto.getSpeedBand()) {

                graphEdgeRepository.updateSpeedBand(dto.getLinkId(), dto.getSpeedBand());

                routeGraphService.updateSpeedBand(dto.getLinkId(), dto.getSpeedBand());

                updated++;
            }
        }

        log.info("Updated speed bands for {} road segments (out of {} checked)", updated, speedBands.size());
    }
}
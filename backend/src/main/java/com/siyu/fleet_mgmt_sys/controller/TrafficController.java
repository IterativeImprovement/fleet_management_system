package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.service.external.LTAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final LTAService LTAService;

    /**
     * Returns all traffic speed bands (all roads, all categories).
     * Data refreshes every 2 minutes on LTA's end.
     */

    @GetMapping("/speedbands")
    public ResponseEntity<List<LtaTrafficSpeedBandResponseDTO>> getAllSpeedBands() {
        List<LtaTrafficSpeedBandResponseDTO> bands = LTAService.getAllSpeedBands();
        return ResponseEntity.ok(bands);
    }

}
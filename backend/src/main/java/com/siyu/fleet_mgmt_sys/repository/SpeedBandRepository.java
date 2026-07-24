package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.RoadSpeedBand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeedBandRepository extends JpaRepository<RoadSpeedBand, String>  {
}

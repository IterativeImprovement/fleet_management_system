package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.Road;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoadRepository extends JpaRepository<Road, Long>  {
    @Query("SELECT rs.id FROM Road rs")
    List<Long> findAllIds();

    @Query("SELECT r FROM Road r LEFT JOIN FETCH r.roadSpeedBand")
    List<Road> findAllWithSpeedBands();

    @Query("SELECT r.id, r.roadName FROM Road r")
    List<Object[]> findAllIdAndName();
}

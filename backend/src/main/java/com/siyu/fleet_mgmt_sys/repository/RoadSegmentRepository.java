package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long>  {
    @Query("SELECT rs.id FROM RoadSegment rs")
    List<Long> findAllIds();
}

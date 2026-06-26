package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.WayPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WayPointRepository extends JpaRepository<WayPoint, Long> {
    @Query("SELECT w.id FROM WayPoint w")
    List<Long> findAllIds();
}

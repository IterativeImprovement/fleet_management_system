package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RobotRepository extends JpaRepository<Robot, Long>, JpaSpecificationExecutor<Robot> {
    @Query("SELECT r.id FROM Robot r")
    List<Long> findAllIds();

    Optional<Robot> findByName(String name);
    @Query("SELECT r.id FROM Robot r WHERE r.simulated = true")
    List<Long> findAllSimulationIds();

    @Query("SELECT r.id FROM Robot r WHERE r.simulated = false")
    List<Long> findAllNonSimulationIds();

    @Query("SELECT r.id FROM Robot r WHERE r.simulated = true AND r.simulationId = :simulationId")
    List<Long> findAllSimulationIds(@Param("simulationId") Long simulationId);

    // Entity lookups for backend-authoritative allocation
    List<Robot> findBySimulatedTrueAndSimulationId(Long simulationId);
    List<Robot> findBySimulatedFalse();
}

package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RobotRepository extends JpaRepository<Robot, Long>, JpaSpecificationExecutor<Robot> {
    @Query("SELECT r.id FROM Robot r")
    List<Long> findAllIds();
}

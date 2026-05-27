package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskClusterRepository extends JpaRepository<Cluster, Long> {
}

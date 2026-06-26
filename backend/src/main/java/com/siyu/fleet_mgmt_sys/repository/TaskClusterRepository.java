package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.task.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskClusterRepository extends JpaRepository<Cluster, Long> {
    List<Cluster> findByTopStandardTaskOrTopLargeTask(Task topStandardTask, Task topLargeTask);
}

package com.siyu.fleet_mgmt_sys.repository;

import com.siyu.fleet_mgmt_sys.model.task.Task;
import com.siyu.fleet_mgmt_sys.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    List<Task> findByStatusAndDependenciesContaining(TaskStatus status, Task dependency);

    @Query("SELECT t.id FROM Task t")
    List<Long> findAllIds();
}

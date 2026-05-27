package com.siyu.fleet_mgmt_sys.specification;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.Task;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class RobotSpecification {
    public static Specification<Robot> filter (
            RobotStatus status,
            String type,
            List<Long> taskIds
            )
    {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("name"), status));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // searches for all robots that does at least one of the tasks the users want
            if (taskIds != null && !taskIds.isEmpty()) {
                Join<Robot, Task> taskJoin = root.join("tasks", JoinType.INNER);
                predicates.add(taskJoin.get("id").in(taskIds));

                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

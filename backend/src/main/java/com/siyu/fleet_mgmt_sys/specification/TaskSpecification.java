package com.siyu.fleet_mgmt_sys.specification;

import com.siyu.fleet_mgmt_sys.model.Task;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TaskSpecification {

    public static Specification<Task> filter(
            Integer priority,
            String type,
            String status,
            String timeLeft,
            String startDateTime,
            String completionDateTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // priority
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            // type
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // startDateTime — after: / before: / on:
            if (startDateTime != null) {
                predicates.add(buildDateTimePredicate(cb, root.get("startDateTime"), startDateTime));
            }

            // completionDateTime — after: / before: / on:
            if (completionDateTime != null) {
                predicates.add(buildDateTimePredicate(cb, root.get("completionDateTime"), completionDateTime));
            }

            // timeLeft — computed as completionDateTime - now, filter with gte: or lte:
            if (timeLeft != null) {
                predicates.add(buildTimeLeftPredicate(cb, root.get("completionDateTime"), timeLeft));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // handles after:2026-05-21T14:49:33 / before: / on:
    private static Predicate buildDateTimePredicate(
            CriteriaBuilder cb,
            Path<LocalDateTime> field,
            String value
    ) {
        if (value.startsWith("after:")) {
            LocalDateTime dt = LocalDateTime.parse(value.substring(6));
            return cb.greaterThan(field, dt);
        } else if (value.startsWith("before:")) {
            LocalDateTime dt = LocalDateTime.parse(value.substring(7));
            return cb.lessThan(field, dt);
        } else if (value.startsWith("on:")) {
            LocalDateTime dt = LocalDateTime.parse(value.substring(3));
            // same day — between start and end of that day
            return cb.between(field, dt.toLocalDate().atStartOfDay(), dt.toLocalDate().atTime(23, 59, 59));
        }
        throw new IllegalArgumentException("Invalid datetime filter format: " + value + ". Use after: / before: / on:");
    }

    // handles gte:Dxx:xx:xx:xx / lte:Dxx:xx:xx:xx (days:hours:minutes:seconds)
    private static Predicate buildTimeLeftPredicate(
            CriteriaBuilder cb,
            Path<LocalDateTime> completionField,
            String value
    ) {
        boolean isGte = value.startsWith("gte:");
        String[] parts = getStrings(value, isGte);
        Duration duration = Duration.ofDays(Long.parseLong(parts[0]))
                .plusHours(Long.parseLong(parts[1]))
                .plusMinutes(Long.parseLong(parts[2]))
                .plusSeconds(Long.parseLong(parts[3]));

        // timeLeft = completionDateTime - now
        // gte: D means completionDateTime >= now + duration
        // lte: D means completionDateTime <= now + duration
        LocalDateTime threshold = LocalDateTime.now().plus(duration);

        if (isGte) {
            return cb.greaterThanOrEqualTo(completionField, threshold);
        } else {
            return cb.lessThanOrEqualTo(completionField, threshold);
        }
    }

    private static String @NonNull [] getStrings(String value, boolean isGte) {
        boolean isLte = value.startsWith("lte:");

        if (!isGte && !isLte) {
            throw new IllegalArgumentException("Invalid timeLeft format: " + value + ". Use gte: or lte:");
        }

        // parse Dxx:xx:xx:xx
        String durationPart = value.substring(4); // strip gte: or lte:
        if (!durationPart.startsWith("D")) {
            throw new IllegalArgumentException("Duration must start with D, e.g. D01:02:30:00");
        }

        return durationPart.substring(1).split(":");
    }
}

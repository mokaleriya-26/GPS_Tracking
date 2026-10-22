package com.gps.tracking.repository;

import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.Trip;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AlertSpecifications {

    /**
     * Builds dynamic JPA Specification for querying alerts based on date range,
     * driver, vehicle, alert types, severity, and status.
     */
    public static Specification<Alert> filterForReport(
            LocalDateTime from,
            LocalDateTime to,
            Long driverId,
            Long vehicleId,
            List<String> alertTypes,
            String severity,
            String status) {

        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }

            if (driverId != null) {
                Join<Alert, Trip> tripJoin = root.join("trip", JoinType.LEFT);
                Predicate directDriver = cb.equal(root.get("driver").get("id"), driverId);
                Predicate tripDriver = cb.and(
                        cb.isNull(root.get("driver")),
                        cb.equal(tripJoin.get("driver").get("id"), driverId)
                );
                predicates.add(cb.or(directDriver, tripDriver));
            }

            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }

            if (alertTypes != null && !alertTypes.isEmpty()) {
                predicates.add(root.get("alertType").in(alertTypes));
            }

            if (severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity)) {
                predicates.add(cb.equal(cb.upper(root.get("severity")), severity.trim().toUpperCase()));
            }

            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

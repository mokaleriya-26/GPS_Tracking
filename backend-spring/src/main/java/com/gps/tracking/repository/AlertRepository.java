package com.gps.tracking.repository;

import com.gps.tracking.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findByReference(String reference);
    Page<Alert> findByOrderByOccurredAtDesc(Pageable pageable);
    Page<Alert> findByVehicleIdOrderByOccurredAtDesc(Long vehicleId, Pageable pageable);
    Page<Alert> findByDriverIdOrderByOccurredAtDesc(Long driverId, Pageable pageable);
    Page<Alert> findByAlertTypeOrderByOccurredAtDesc(String alertType, Pageable pageable);
    Page<Alert> findByStatusOrderByOccurredAtDesc(String status, Pageable pageable);
    Page<Alert> findBySeverityOrderByOccurredAtDesc(String severity, Pageable pageable);

    List<Alert> findByDriverIdAndOccurredAtBetween(Long driverId, LocalDateTime from, LocalDateTime to);
    List<Alert> findByVehicleIdAndOccurredAtBetween(Long vehicleId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.driver.id = :driverId AND a.occurredAt BETWEEN :from AND :to")
    long countByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    /** Count only live (non-silenced) alerts — used for safety scoring so CSV imports don't penalise drivers */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.driver.id = :driverId AND a.occurredAt BETWEEN :from AND :to AND a.deliveryStatus <> 'SILENCED'")
    long countLiveByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.driver.id = :driverId AND a.occurredAt BETWEEN :from AND :to AND a.deliveryStatus <> 'SILENCED' GROUP BY a.alertType")
    List<Object[]> countByTypeForDriver(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'OPEN'")
    long countOpenAlerts();

    /** Total alerts in a date range (all statuses — used for Overall Report) */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.occurredAt BETWEEN :from AND :to")
    long countInDateRange(LocalDateTime from, LocalDateTime to);

    /** Alert counts grouped by type for a date range (Overall Report — alert type summary) */
    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.occurredAt BETWEEN :from AND :to GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countByTypeInDateRange(LocalDateTime from, LocalDateTime to);

    /**
     * Per-driver alert breakdown by type for a date range.
     * Returns [driverId, driverCode, driverName, alertType, count]
     */
    @Query("""
        SELECT a.driver.id, a.driver.code, a.driver.name, a.alertType, COUNT(a)
        FROM Alert a
        WHERE a.driver IS NOT NULL AND a.occurredAt BETWEEN :from AND :to
        GROUP BY a.driver.id, a.driver.code, a.driver.name, a.alertType
        ORDER BY a.driver.name, a.alertType
        """)
    List<Object[]> driverAlertBreakdownInDateRange(LocalDateTime from, LocalDateTime to);
}

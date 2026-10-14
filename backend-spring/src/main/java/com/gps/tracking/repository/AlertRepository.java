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

    @Query("SELECT DISTINCT a FROM Alert a LEFT JOIN a.trip t " +
           "WHERE (a.driver.id = :driverId OR (a.driver IS NULL AND t.driver.id = :driverId)) " +
           "AND a.occurredAt >= :from AND a.occurredAt < :to ORDER BY a.occurredAt DESC")
    List<Alert> findAlertsByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT DISTINCT a FROM Alert a WHERE a.occurredAt >= :from AND a.occurredAt < :to ORDER BY a.occurredAt DESC")
    List<Alert> findAlertsInDateRange(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT a) FROM Alert a LEFT JOIN a.trip t " +
           "WHERE (a.driver.id = :driverId OR (a.driver IS NULL AND t.driver.id = :driverId)) " +
           "AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countAlertsByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.driver.id = :driverId AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.driver.id = :driverId AND a.occurredAt >= :from AND a.occurredAt < :to AND a.deliveryStatus <> 'SILENCED'")
    long countLiveByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.alertType, COUNT(DISTINCT a) FROM Alert a LEFT JOIN a.trip t " +
           "WHERE (a.driver.id = :driverId OR (a.driver IS NULL AND t.driver.id = :driverId)) " +
           "AND a.occurredAt >= :from AND a.occurredAt < :to GROUP BY a.alertType ORDER BY COUNT(DISTINCT a) DESC")
    List<Object[]> countAlertsByTypeForDriver(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.alertType, COUNT(a) FROM Alert a " +
           "WHERE a.driver.id = :driverId AND a.occurredAt >= :from AND a.occurredAt < :to AND a.deliveryStatus <> 'SILENCED' " +
           "GROUP BY a.alertType")
    List<Object[]> countByTypeForDriver(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT a) FROM Alert a LEFT JOIN a.trip t " +
           "WHERE a.vehicle.id = :vehicleId " +
           "AND (a.driver.id = :driverId OR (a.driver IS NULL AND t.driver.id = :driverId)) " +
           "AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countAlertsByVehicleAndDriverAndDateRange(Long vehicleId, Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'OPEN'")
    long countOpenAlerts();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.occurredAt >= :from AND a.occurredAt < :to")
    long countInDateRange(LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.occurredAt >= :from AND a.occurredAt < :to GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countByTypeInDateRange(LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT COALESCE(a.driver.id, t.driver.id), COALESCE(a.driver.code, t.driver.code), COALESCE(a.driver.name, t.driver.name), a.alertType, COUNT(DISTINCT a)
        FROM Alert a LEFT JOIN a.trip t
        WHERE (a.driver IS NOT NULL OR t.driver IS NOT NULL) AND a.occurredAt >= :from AND a.occurredAt < :to
        GROUP BY COALESCE(a.driver.id, t.driver.id), COALESCE(a.driver.code, t.driver.code), COALESCE(a.driver.name, t.driver.name), a.alertType
        ORDER BY COALESCE(a.driver.name, t.driver.name), a.alertType
        """)
    List<Object[]> driverAlertBreakdownInDateRange(LocalDateTime from, LocalDateTime to);

    long countByVehicleId(Long vehicleId);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.vehicle.id = :vehicleId AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countByVehicleIdAndDateRange(Long vehicleId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.alertType, COUNT(a) FROM Alert a WHERE a.vehicle.id = :vehicleId GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countByTypeForVehicle(Long vehicleId);

    @Query("SELECT a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber, COUNT(a) FROM Alert a WHERE a.vehicle IS NOT NULL GROUP BY a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber ORDER BY COUNT(a) DESC")
    List<Object[]> findTopVehiclesByAlertCount();

    @Query("SELECT a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber, COUNT(a) FROM Alert a WHERE a.vehicle IS NOT NULL AND a.occurredAt >= :from AND a.occurredAt < :to GROUP BY a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber ORDER BY COUNT(a) DESC")
    List<Object[]> findTopVehiclesByAlertCountInDateRange(LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber, COUNT(a) FROM Alert a WHERE a.vehicle IS NOT NULL AND LOWER(a.alertType) LIKE LOWER(CONCAT('%', :alertType, '%')) GROUP BY a.vehicle.id, a.vehicle.code, a.vehicle.registrationNumber ORDER BY COUNT(a) DESC")
    List<Object[]> findTopVehiclesByAlertType(String alertType);

    @Query("SELECT COUNT(a) FROM Alert a WHERE LOWER(a.alertType) LIKE LOWER(CONCAT('%', :alertType, '%'))")
    long countByAlertTypeLike(String alertType);

    @Query("SELECT COUNT(a) FROM Alert a WHERE LOWER(a.alertType) LIKE LOWER(CONCAT('%', :alertType, '%')) AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countByAlertTypeLikeInDateRange(String alertType, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE LOWER(a.alertType) LIKE LOWER(CONCAT('%', :alertType, '%')) AND a.status = 'OPEN'")
    long countOpenByAlertTypeLike(String alertType);

    @Query("SELECT a.alertType, COUNT(a) FROM Alert a GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countAllGroupedByType();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :severity")
    long countBySeverity(String severity);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :severity AND a.occurredAt >= :from AND a.occurredAt < :to")
    long countBySeverityInDateRange(String severity, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = 'CRITICAL' AND a.status = 'OPEN'")
    long countOpenCriticalAlerts();

    @Query("SELECT a FROM Alert a WHERE a.severity = 'CRITICAL' AND a.status = 'OPEN' ORDER BY a.occurredAt DESC")
    List<Alert> findOpenCriticalAlerts();

    @Query(value = "SELECT CAST(occurred_at AS DATE) as alert_date, COUNT(*) as alert_count FROM alerts GROUP BY CAST(occurred_at AS DATE) ORDER BY alert_count DESC LIMIT 1", nativeQuery = true)
    List<Object[]> findDayWithMostAlerts();
}

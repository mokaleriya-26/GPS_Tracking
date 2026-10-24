package com.gps.tracking.repository;

import com.gps.tracking.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {
    Optional<Alert> findByReference(String reference);
    Page<Alert> findByOrderByOccurredAtDesc(Pageable pageable);
    Page<Alert> findByVehicleIdOrderByOccurredAtDesc(Long vehicleId, Pageable pageable);
    Page<Alert> findByDriverIdOrderByOccurredAtDesc(Long driverId, Pageable pageable);
    Page<Alert> findByAlertTypeOrderByOccurredAtDesc(String alertType, Pageable pageable);
    Page<Alert> findByStatusOrderByOccurredAtDesc(String status, Pageable pageable);
    Page<Alert> findBySeverityOrderByOccurredAtDesc(String severity, Pageable pageable);

    List<Alert> findByTripId(Long tripId);
    List<Alert> findByIsSimulationTrue();
    List<Alert> findByTripIdAndIsSimulationTrue(Long tripId);

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

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status = 'HISTORICAL'")
    long countHistoricalAlerts();

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

    /**
     * Parameterized Driver Alert Rankings:
     * Directly queries PostgreSQL grouping by driver, with optional alertType, severity, and date range.
     * Excludes dummy / test entries.
     */
    @Query("""
        SELECT d.id, d.code, d.name, COUNT(a.id)
        FROM Driver d
        LEFT JOIN Alert a ON a.driver.id = d.id
            AND (CAST(:alertType AS string) IS NULL OR LOWER(a.alertType) LIKE LOWER(CONCAT('%', CAST(:alertType AS string), '%')))
            AND (CAST(:severity AS string) IS NULL OR UPPER(a.severity) = UPPER(CAST(:severity AS string)))
            AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
            AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to)
        WHERE d.active = true AND d.name NOT ILIKE '%no email%' AND d.name NOT ILIKE '%test%'
        GROUP BY d.id, d.code, d.name
        ORDER BY COUNT(a.id) DESC
    """)
    List<Object[]> findDriverAlertRankings(
        @Param("alertType") String alertType,
        @Param("severity") String severity,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT d.id, d.code, d.name, COUNT(a.id)
        FROM Driver d
        LEFT JOIN Alert a ON a.driver.id = d.id
            AND (CAST(:alertType AS string) IS NULL OR LOWER(a.alertType) LIKE LOWER(CONCAT('%', CAST(:alertType AS string), '%')))
            AND (CAST(:severity AS string) IS NULL OR UPPER(a.severity) = UPPER(CAST(:severity AS string)))
            AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
            AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to)
        WHERE d.active = true AND d.name NOT ILIKE '%no email%' AND d.name NOT ILIKE '%test%'
        GROUP BY d.id, d.code, d.name
        ORDER BY COUNT(a.id) ASC
    """)
    List<Object[]> findDriverAlertRankingsAsc(
        @Param("alertType") String alertType,
        @Param("severity") String severity,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COUNT(a.id) FROM Alert a
        WHERE a.driver.id = :driverId
            AND (CAST(:alertType AS string) IS NULL OR LOWER(a.alertType) LIKE LOWER(CONCAT('%', CAST(:alertType AS string), '%')))
            AND (CAST(:severity AS string) IS NULL OR UPPER(a.severity) = UPPER(CAST(:severity AS string)))
            AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
            AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to)
    """)
    long countAlertsByDriverWithFilters(
        @Param("driverId") Long driverId,
        @Param("alertType") String alertType,
        @Param("severity") String severity,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COUNT(a.id) FROM Alert a
        WHERE (CAST(:alertType AS string) IS NULL OR LOWER(a.alertType) LIKE LOWER(CONCAT('%', CAST(:alertType AS string), '%')))
            AND (CAST(:severity AS string) IS NULL OR UPPER(a.severity) = UPPER(CAST(:severity AS string)))
            AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
            AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt < :to)
    """)
    long countAlertsWithFilters(
        @Param("alertType") String alertType,
        @Param("severity") String severity,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    long countByDriverId(Long driverId);

    @Query("SELECT a.driver.id, COUNT(a) FROM Alert a WHERE a.driver IS NOT NULL GROUP BY a.driver.id")
    List<Object[]> getAllDriverAlertCounts();
}

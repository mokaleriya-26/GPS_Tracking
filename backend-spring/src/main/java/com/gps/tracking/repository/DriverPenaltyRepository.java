package com.gps.tracking.repository;

import com.gps.tracking.entity.DriverPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DriverPenaltyRepository extends JpaRepository<DriverPenalty, Long> {

    List<DriverPenalty> findByDriverIdOrderByDateAppliedDesc(Long driverId);

    List<DriverPenalty> findByDriverIdAndDateAppliedBetweenOrderByDateAppliedDesc(
            Long driverId, LocalDateTime from, LocalDateTime to);

    List<DriverPenalty> findByDriverIdAndStatusAndDeductFromScoreTrue(Long driverId, String status);

    List<DriverPenalty> findByDriverIdAndStatusAndDeductFromScoreTrueAndDateAppliedBetween(
            Long driverId, String status, LocalDateTime from, LocalDateTime to);

    long countByDriverIdAndAlertIdAndStatus(Long driverId, Long alertId, String status);

    List<DriverPenalty> findByAlertId(Long alertId);

    java.util.Optional<DriverPenalty> findByAlertIdAndAlertType(Long alertId, String alertType);

    long countByDriverIdAndAlertTypeAndStatusNot(Long driverId, String alertType, String status);

    long countByDriverIdAndClassificationAndStatus(Long driverId, String classification, String status);

    long countByClassificationAndStatus(String classification, String status);

    @Query("SELECT COALESCE(SUM(p.monetaryAmount), 0) FROM DriverPenalty p WHERE p.driver.id = :driverId AND p.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveMonetaryAmountByDriverId(Long driverId);

    @Query("SELECT COALESCE(SUM(p.monetaryAmount), 0) FROM DriverPenalty p WHERE p.status = 'ACTIVE'")
    java.math.BigDecimal sumTotalActiveMonetaryAmount();

    @Query("SELECT p.alertType, SUM(p.monetaryAmount) FROM DriverPenalty p WHERE p.driver.id = :driverId AND p.status = 'ACTIVE' GROUP BY p.alertType")
    List<Object[]> sumMonetaryByDriverGroupedByAlertType(Long driverId);

    @Query("SELECT p.alertType, COUNT(p) FROM DriverPenalty p WHERE p.driver.id = :driverId AND p.status <> 'REVOKED' GROUP BY p.alertType")
    List<Object[]> countOccurrencesByDriverGroupedByAlertType(Long driverId);

    @Query("SELECT p.alertType, SUM(p.monetaryAmount) FROM DriverPenalty p WHERE p.status = 'ACTIVE' GROUP BY p.alertType")
    List<Object[]> sumTotalMonetaryGroupedByAlertType();

    @Query("SELECT p.alertType, COUNT(p) FROM DriverPenalty p WHERE p.status <> 'REVOKED' GROUP BY p.alertType")
    List<Object[]> countTotalAlertsGroupedByAlertType();

    @Query("SELECT p.driver.id, p.driver.code, p.driver.name, SUM(p.monetaryAmount) FROM DriverPenalty p " +
           "WHERE p.status = 'ACTIVE' " +
           "GROUP BY p.driver.id, p.driver.code, p.driver.name " +
           "ORDER BY SUM(p.monetaryAmount) DESC")
    List<Object[]> findDriverPenaltyRankings();

    java.util.Optional<DriverPenalty> findFirstByDriverIdOrderByDateAppliedDesc(Long driverId);

    java.util.Optional<DriverPenalty> findFirstByOrderByDateAppliedDesc();

    List<DriverPenalty> findTop10ByOrderByDateAppliedDesc();

    @Query("SELECT COALESCE(SUM(p.penaltyPoints), 0) FROM DriverPenalty p " +
           "WHERE p.driver.id = :driverId AND p.status = 'ACTIVE' AND p.deductFromScore = true " +
           "AND p.dateApplied >= :from AND p.dateApplied < :to")
    Double sumActiveSafetyPenaltyPointsInDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.penaltyPoints), 0) FROM DriverPenalty p " +
           "WHERE p.driver.id = :driverId AND p.status = 'ACTIVE' AND p.deductFromScore = true")
    Double sumActiveSafetyPenaltyPoints(Long driverId);
}

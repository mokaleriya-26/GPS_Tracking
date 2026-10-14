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

    @Query("SELECT COALESCE(SUM(p.penaltyPoints), 0) FROM DriverPenalty p " +
           "WHERE p.driver.id = :driverId AND p.status = 'ACTIVE' AND p.deductFromScore = true " +
           "AND p.dateApplied >= :from AND p.dateApplied < :to")
    Double sumActiveSafetyPenaltyPointsInDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.penaltyPoints), 0) FROM DriverPenalty p " +
           "WHERE p.driver.id = :driverId AND p.status = 'ACTIVE' AND p.deductFromScore = true")
    Double sumActiveSafetyPenaltyPoints(Long driverId);
}

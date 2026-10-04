package com.gps.tracking.repository;

import com.gps.tracking.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    Page<Trip> findByVehicleIdOrderByStartTimeDesc(Long vehicleId, Pageable pageable);
    Page<Trip> findByDriverIdOrderByStartTimeDesc(Long driverId, Pageable pageable);
    List<Trip> findByVehicleIdAndStartTimeBetweenOrderByStartTimeDesc(Long vehicleId, LocalDateTime from, LocalDateTime to);
    List<Trip> findByDriverIdAndStartTimeBetweenOrderByStartTimeDesc(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.driver.id = :driverId AND t.startTime BETWEEN :from AND :to")
    long countByDriverIdAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.driver.id = :driverId")
    long countByDriverId(Long driverId);

    @Query("SELECT SUM(t.distanceKm) FROM Trip t WHERE t.driver.id = :driverId AND t.startTime BETWEEN :from AND :to")
    Double sumDistanceByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to);

    /** Distinct vehicles that started at least one trip in the period (entry events) */
    @Query("SELECT COUNT(DISTINCT t.vehicle.id) FROM Trip t WHERE t.startTime BETWEEN :from AND :to")
    long countDistinctVehiclesWithTripStarted(LocalDateTime from, LocalDateTime to);

    /** Distinct vehicles that completed at least one trip in the period (exit events — endTime not null) */
    @Query("SELECT COUNT(DISTINCT t.vehicle.id) FROM Trip t WHERE t.endTime IS NOT NULL AND t.endTime BETWEEN :from AND :to")
    long countDistinctVehiclesWithTripCompleted(LocalDateTime from, LocalDateTime to);

    /** Trips that started in the given date range */
    @Query("SELECT t FROM Trip t WHERE t.startTime BETWEEN :from AND :to ORDER BY t.startTime DESC")
    List<Trip> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByVehicleId(Long vehicleId);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.vehicle.id = :vehicleId AND t.startTime BETWEEN :from AND :to")
    long countByVehicleIdAndDateRange(Long vehicleId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.distanceKm), 0) FROM Trip t WHERE t.vehicle.id = :vehicleId")
    Double sumDistanceByVehicleId(Long vehicleId);

    @Query("SELECT COALESCE(SUM(t.distanceKm), 0) FROM Trip t WHERE t.vehicle.id = :vehicleId AND t.startTime BETWEEN :from AND :to")
    Double sumDistanceByVehicleIdAndDateRange(Long vehicleId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.distanceKm), 0) FROM Trip t")
    Double sumTotalDistance();

    @Query("SELECT COALESCE(SUM(t.distanceKm), 0) FROM Trip t WHERE t.startTime BETWEEN :from AND :to")
    Double sumDistanceInDateRange(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.startTime BETWEEN :from AND :to")
    long countInDateRange(LocalDateTime from, LocalDateTime to);
}

package com.gps.tracking.repository;

import com.gps.tracking.entity.DriverDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverDailyStatsRepository extends JpaRepository<DriverDailyStats, Long> {
    List<DriverDailyStats> findByDriverIdAndStatDateBetweenOrderByStatDateAsc(Long driverId, LocalDate from, LocalDate to);
    Optional<DriverDailyStats> findByDriverIdAndStatDate(Long driverId, LocalDate statDate);

    @Query("SELECT s FROM DriverDailyStats s WHERE s.statDate BETWEEN :from AND :to ORDER BY s.safetyScore DESC")
    List<DriverDailyStats> findRankingByDateRange(LocalDate from, LocalDate to);

    @Query("SELECT s.driver.id, AVG(s.safetyScore) as avgScore FROM DriverDailyStats s WHERE s.statDate BETWEEN :from AND :to GROUP BY s.driver.id ORDER BY avgScore DESC")
    List<Object[]> getDriverRanking(LocalDate from, LocalDate to);
}

package com.gps.tracking.service;

import com.gps.tracking.entity.DriverDailyStats;
import com.gps.tracking.repository.DriverDailyStatsRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {
    private final DriverDailyStatsRepository statsRepository;
    private final DriverRepository driverRepository;

    public List<DriverDailyStats> getDriverStats(Long driverId, LocalDate from, LocalDate to) {
        if (!driverRepository.existsById(driverId))
            throw new ResourceNotFoundException("Driver", driverId);
        return statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driverId, from, to);
    }

    public Optional<DriverDailyStats> getDriverStatsByDate(Long driverId, LocalDate date) {
        return statsRepository.findByDriverIdAndStatDate(driverId, date);
    }
}

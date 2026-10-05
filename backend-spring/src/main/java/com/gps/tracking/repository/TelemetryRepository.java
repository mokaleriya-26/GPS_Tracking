package com.gps.tracking.repository;

import com.gps.tracking.entity.Telemetry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    Page<Telemetry> findByVehicleIdOrderByRecordedAtDesc(Long vehicleId, Pageable pageable);
    List<Telemetry> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long vehicleId, LocalDateTime from, LocalDateTime to);
    Optional<Telemetry> findTopByVehicleIdOrderByRecordedAtDesc(Long vehicleId);
    Optional<Telemetry> findTopByOrderBySpeedKmphDesc();
}

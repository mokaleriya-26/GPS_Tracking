package com.gps.tracking.repository;

import com.gps.tracking.entity.CostRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CostRepository extends JpaRepository<CostRecord, Long> {
    List<CostRecord> findByVehicleIdOrderByIncurredOnDesc(Long vehicleId);
    List<CostRecord> findByVehicleIdAndIncurredOnBetween(Long vehicleId, LocalDate from, LocalDate to);
    @Query("SELECT SUM(c.amountInr) FROM CostRecord c WHERE c.vehicle.id = :vehicleId AND c.incurredOn BETWEEN :from AND :to")
    Double sumCostByVehicleAndDateRange(Long vehicleId, LocalDate from, LocalDate to);
}

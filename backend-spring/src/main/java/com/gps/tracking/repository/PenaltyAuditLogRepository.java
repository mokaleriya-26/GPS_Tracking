package com.gps.tracking.repository;

import com.gps.tracking.entity.PenaltyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyAuditLogRepository extends JpaRepository<PenaltyAuditLog, Long> {
    List<PenaltyAuditLog> findByPenaltyIdOrderByCreatedAtDesc(Long penaltyId);
}

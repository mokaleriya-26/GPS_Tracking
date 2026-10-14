package com.gps.tracking.repository;

import com.gps.tracking.entity.AlertPenaltyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertPenaltyConfigRepository extends JpaRepository<AlertPenaltyConfig, Long> {
    Optional<AlertPenaltyConfig> findByAlertType(String alertType);
    List<AlertPenaltyConfig> findByIsActiveTrue();
}

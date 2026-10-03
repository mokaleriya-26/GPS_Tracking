package com.gps.tracking.repository;

import com.gps.tracking.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByCode(String code);
    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);
    Optional<Vehicle> findByImei(String imei);
    List<Vehicle> findByActiveTrue();
    List<Vehicle> findByDriverId(Long driverId);
}

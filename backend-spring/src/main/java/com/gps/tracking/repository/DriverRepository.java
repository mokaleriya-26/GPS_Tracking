package com.gps.tracking.repository;

import com.gps.tracking.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByCode(String code);
    Optional<Driver> findByNameIgnoreCase(String name);
    List<Driver> findByActiveTrue();
    List<Driver> findByActiveTrueOrderByIdAsc();
    List<Driver> findAllByOrderByIdAsc();

    @Query("SELECT d FROM Driver d WHERE d.code NOT LIKE 'DRV_PA_%' AND d.code NOT LIKE 'DRV_PB_%' AND d.code NOT LIKE 'DRV_L_%' AND d.code NOT LIKE 'DRV_NORM_%' ORDER BY d.id ASC")
    List<Driver> findFleetDriversOrderByIdAsc();

    @Query("SELECT d FROM Driver d WHERE d.active = true AND d.code NOT LIKE 'DRV_PA_%' AND d.code NOT LIKE 'DRV_PB_%' AND d.code NOT LIKE 'DRV_L_%' AND d.code NOT LIKE 'DRV_NORM_%' ORDER BY d.id ASC")
    List<Driver> findActiveFleetDriversOrderByIdAsc();

    @Query("SELECT d FROM Driver d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(d.code) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY d.id ASC")
    List<Driver> searchByName(@Param("name") String name);
}

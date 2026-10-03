package com.gps.tracking.repository;

import com.gps.tracking.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByCode(String code);
    Optional<Driver> findByNameIgnoreCase(String name);
    List<Driver> findByActiveTrue();
    @Query("SELECT d FROM Driver d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Driver> searchByName(String name);
}

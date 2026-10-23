package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.dto.LoginRequestDTO;
import com.gps.tracking.dto.LoginResponseDTO;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.security.JwtTokenProvider;
import com.gps.tracking.security.SecurityUtils;
import com.gps.tracking.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Role-based authentication for Admin and Drivers")
public class AuthController {

    private final VehicleRepository vehicleRepository;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user (Admin: test/test; Driver: Vehicle ID as both username & password)")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@RequestBody LoginRequestDTO req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Username and password are required."));
        }

        String username = req.getUsername().trim();
        String password = req.getPassword().trim();

        // 1. Check Admin Demo Credentials: test / test
        if ("test".equalsIgnoreCase(username) && "test".equals(password)) {
            UserPrincipal principal = UserPrincipal.builder()
                    .username("test")
                    .role("ROLE_ADMIN")
                    .driverName("Admin User")
                    .build();

            String token = tokenProvider.generateToken(principal);

            LoginResponseDTO resp = LoginResponseDTO.builder()
                    .token(token)
                    .role("ADMIN")
                    .username("test")
                    .driverName("Admin User")
                    .build();

            log.info("Admin login successful for username: test");
            return ResponseEntity.ok(ApiResponse.ok(resp, "Admin login successful"));
        }

        // 2. Check Driver Login: Vehicle ID used as both username and password (e.g. VH001 / VH001)
        if (username.equalsIgnoreCase(password)) {
            Optional<Vehicle> vehicleOpt = vehicleRepository.findByCodeIgnoreCase(username);

            // Fallback: try by registration number or numeric ID if vehicle code not found
            if (vehicleOpt.isEmpty()) {
                vehicleOpt = vehicleRepository.findByRegistrationNumberIgnoreCase(username);
            }
            if (vehicleOpt.isEmpty()) {
                try {
                    Long id = Long.parseLong(username);
                    vehicleOpt = vehicleRepository.findById(id);
                } catch (NumberFormatException ignored) {}
            }

            if (vehicleOpt.isEmpty()) {
                log.warn("Driver login failed: Vehicle ID '{}' not found", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Vehicle ID '" + username + "' not found. Please verify your Vehicle ID."));
            }

            Vehicle vehicle = vehicleOpt.get();
            Driver driver = vehicle.getDriver();

            if (driver == null) {
                log.warn("Driver login failed: Vehicle '{}' ({}) has no assigned driver", vehicle.getCode(), vehicle.getRegistrationNumber());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Vehicle '" + vehicle.getCode() + "' is not currently assigned to any driver. Please contact fleet administration."));
            }

            UserPrincipal principal = UserPrincipal.builder()
                    .username(vehicle.getCode())
                    .role("ROLE_USER")
                    .driverId(driver.getId())
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .vehicleId(vehicle.getId())
                    .vehicleCode(vehicle.getCode())
                    .build();

            String token = tokenProvider.generateToken(principal);

            LoginResponseDTO resp = LoginResponseDTO.builder()
                    .token(token)
                    .role("USER")
                    .username(vehicle.getCode())
                    .driverId(driver.getId())
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .vehicleId(vehicle.getId())
                    .vehicleCode(vehicle.getCode())
                    .vehicleRegistrationNumber(vehicle.getRegistrationNumber())
                    .build();

            log.info("Driver login successful: Driver '{}' ({}) via Vehicle '{}'", driver.getName(), driver.getCode(), vehicle.getCode());
            return ResponseEntity.ok(ApiResponse.ok(resp, "Driver login successful"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials. Enter 'test' / 'test' for Admin or your Vehicle ID for Driver."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> getCurrentUser() {
        UserPrincipal principal = SecurityUtils.getCurrentUser();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        LoginResponseDTO resp = LoginResponseDTO.builder()
                .role(principal.isAdmin() ? "ADMIN" : "USER")
                .username(principal.getUsername())
                .driverId(principal.getDriverId())
                .driverCode(principal.getDriverCode())
                .driverName(principal.getDriverName())
                .vehicleId(principal.getVehicleId())
                .vehicleCode(principal.getVehicleCode())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user and invalidate session context")
    public ResponseEntity<ApiResponse<String>> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }
}

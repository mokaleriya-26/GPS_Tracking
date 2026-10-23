package com.gps.tracking.security;

import com.gps.tracking.controller.AuthController;
import com.gps.tracking.dto.*;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthAndDataIsolationTest {

    @Mock
    private VehicleRepository vehicleRepository;

    private JwtTokenProvider tokenProvider;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "secretKey", "TestSecretKeyForAntigravitySecureTokenGeneration2026!!");
        ReflectionTestUtils.setField(tokenProvider, "expirationMs", 3600000L);
        authController = new AuthController(vehicleRepository, tokenProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Admin Login Success with test/test")
    void testAdminLoginSuccess() {
        LoginRequestDTO req = new LoginRequestDTO("test", "test");
        ResponseEntity<ApiResponse<LoginResponseDTO>> resp = authController.login(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
        LoginResponseDTO data = resp.getBody().getData();
        assertEquals("ADMIN", data.getRole());
        assertEquals("test", data.getUsername());
        assertNotNull(data.getToken());
        assertTrue(tokenProvider.validateToken(data.getToken()));
    }

    @Test
    @DisplayName("Driver Login Success: Vehicle ID resolves assigned driver")
    void testDriverLoginSuccess() {
        Driver driver = Driver.builder()
                .id(1L)
                .code("DRV001")
                .name("Aarav Sharma")
                .phone("9876543210")
                .build();

        Vehicle vehicle = Vehicle.builder()
                .id(10L)
                .code("VH001")
                .registrationNumber("MH03AC4582")
                .driver(driver)
                .active(true)
                .build();

        when(vehicleRepository.findByCodeIgnoreCase("VH001")).thenReturn(Optional.of(vehicle));

        LoginRequestDTO req = new LoginRequestDTO("VH001", "VH001");
        ResponseEntity<ApiResponse<LoginResponseDTO>> resp = authController.login(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
        LoginResponseDTO data = resp.getBody().getData();
        assertEquals("USER", data.getRole());
        assertEquals(1L, data.getDriverId());
        assertEquals("DRV001", data.getDriverCode());
        assertEquals("Aarav Sharma", data.getDriverName());
        assertEquals("VH001", data.getVehicleCode());
        assertEquals(10L, data.getVehicleId());
        assertNotNull(data.getToken());
        assertTrue(tokenProvider.validateToken(data.getToken()));

        UserPrincipal principal = tokenProvider.getPrincipalFromToken(data.getToken());
        assertEquals(1L, principal.getDriverId());
        assertEquals("VH001", principal.getUsername());
    }

    @Test
    @DisplayName("Driver Login Failed: Non-existent Vehicle ID")
    void testDriverLoginVehicleNotFound() {
        when(vehicleRepository.findByCodeIgnoreCase("VH999")).thenReturn(Optional.empty());
        when(vehicleRepository.findByRegistrationNumberIgnoreCase("VH999")).thenReturn(Optional.empty());

        LoginRequestDTO req = new LoginRequestDTO("VH999", "VH999");
        ResponseEntity<ApiResponse<LoginResponseDTO>> resp = authController.login(req);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
        assertTrue(resp.getBody().getMessage().contains("Vehicle ID 'VH999' not found"));
    }

    @Test
    @DisplayName("Driver Login Failed: Vehicle has no assigned driver")
    void testDriverLoginUnassignedVehicle() {
        Vehicle vehicle = Vehicle.builder()
                .id(20L)
                .code("VH020")
                .registrationNumber("MH04TEST20")
                .driver(null)
                .active(true)
                .build();

        when(vehicleRepository.findByCodeIgnoreCase("VH020")).thenReturn(Optional.of(vehicle));

        LoginRequestDTO req = new LoginRequestDTO("VH020", "VH020");
        ResponseEntity<ApiResponse<LoginResponseDTO>> resp = authController.login(req);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
        assertTrue(resp.getBody().getMessage().contains("not currently assigned to any driver"));
    }

    @Test
    @DisplayName("Driver Login Failed: Password does not match Vehicle ID")
    void testDriverLoginInvalidPassword() {
        LoginRequestDTO req = new LoginRequestDTO("VH001", "wrong_password");
        ResponseEntity<ApiResponse<LoginResponseDTO>> resp = authController.login(req);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
        assertTrue(resp.getBody().getMessage().contains("Invalid credentials"));
    }

    @Test
    @DisplayName("Data Isolation: Driver A attempting to access Driver B data throws AccessDeniedException")
    void testDataIsolationDriverDeniedAccessToOtherDriver() {
        UserPrincipal driverA = UserPrincipal.builder()
                .username("VH001")
                .role("ROLE_USER")
                .driverId(1L)
                .driverCode("DRV001")
                .driverName("Aarav Sharma")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(driverA, null, driverA.getAuthorities())
        );

        // Driver A accessing Driver 1's data -> Allowed
        assertDoesNotThrow(() -> SecurityUtils.validateDriverAccess(1L));

        // Driver A accessing Driver 2's data -> Forbidden
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> SecurityUtils.validateDriverAccess(2L));
        assertTrue(ex.getMessage().contains("Data isolation violation"));
    }

    @Test
    @DisplayName("Data Isolation: Driver cannot perform Admin operations")
    void testDriverCannotPerformAdminOperations() {
        UserPrincipal driverA = UserPrincipal.builder()
                .username("VH001")
                .role("ROLE_USER")
                .driverId(1L)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(driverA, null, driverA.getAuthorities())
        );

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, SecurityUtils::requireAdmin);
        assertTrue(ex.getMessage().contains("Administrative privileges required"));
    }

    @Test
    @DisplayName("Data Isolation: Admin has full access to any driver records")
    void testAdminHasFullAccess() {
        UserPrincipal admin = UserPrincipal.builder()
                .username("test")
                .role("ROLE_ADMIN")
                .driverName("Admin User")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities())
        );

        assertDoesNotThrow(() -> SecurityUtils.validateDriverAccess(1L));
        assertDoesNotThrow(() -> SecurityUtils.validateDriverAccess(999L));
        assertDoesNotThrow(SecurityUtils::requireAdmin);
    }
}

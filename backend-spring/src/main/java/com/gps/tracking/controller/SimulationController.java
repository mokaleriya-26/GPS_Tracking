package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.SimulationRouteProvider;
import com.gps.tracking.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Simulation", description = "Interactive Live Truck Simulation and Alert Demonstration API")
public class SimulationController {

    private final SimulationService simulationService;
    private final SimulationRouteProvider routeProvider;

    @GetMapping("/routes")
    @Operation(summary = "Get all available demonstration routes")
    public ResponseEntity<List<SimulationRouteDTO>> getRoutes() {
        return ResponseEntity.ok(routeProvider.getAllRoutes());
    }

    @GetMapping("/status")
    @Operation(summary = "Get current simulation telemetry and alert state")
    public ResponseEntity<SimulationStateDTO> getStatus() {
        return ResponseEntity.ok(simulationService.getCurrentState());
    }

    @PostMapping("/start")
    @Operation(summary = "Start or restart a truck simulation journey")
    public ResponseEntity<SimulationStateDTO> startSimulation(@RequestBody(required = false) Map<String, Object> payload) {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        String routeId = "mumbai-pune-expressway";
        Integer multiplier = 1;

        if (payload != null) {
            if (payload.containsKey("routeId") && payload.get("routeId") != null) {
                routeId = payload.get("routeId").toString();
            }
            if (payload.containsKey("multiplier") && payload.get("multiplier") != null) {
                try {
                    multiplier = Integer.parseInt(payload.get("multiplier").toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return ResponseEntity.ok(simulationService.startSimulation(routeId, multiplier));
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause the running simulation")
    public ResponseEntity<SimulationStateDTO> pauseSimulation() {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.pauseSimulation());
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume the paused simulation")
    public ResponseEntity<SimulationStateDTO> resumeSimulation() {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.resumeSimulation());
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the simulation and record final status")
    public ResponseEntity<SimulationStateDTO> stopSimulation() {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.stopSimulation());
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset simulation and safely clear simulated trip records")
    public ResponseEntity<SimulationStateDTO> resetSimulation() {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.resetSimulation());
    }

    @PostMapping("/step")
    @Operation(summary = "Execute one telemetry step along the route and evaluate alert rules")
    public ResponseEntity<SimulationStateDTO> stepSimulation() {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.stepSimulation());
    }

    @PostMapping("/trigger-event")
    @Operation(summary = "Trigger a manual event scenario (Overspeeding, Harsh Braking, Harsh Acceleration, GPS Disconnect, Normal)")
    public ResponseEntity<SimulationStateDTO> triggerEvent(@RequestBody SimulationEventRequestDTO req) {
        com.gps.tracking.security.SecurityUtils.requireAdmin();
        return ResponseEntity.ok(simulationService.triggerEvent(req.getEventType(), req.getOverrideSpeedKmph(), req.getEventId()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get final trip summary and statistics")
    public ResponseEntity<SimulationSummaryDTO> getSummary() {
        SimulationSummaryDTO summary = simulationService.getSimulationSummary();
        if (summary == null) {
            // Generate current on-demand summary if trip is in progress
            SimulationStateDTO state = simulationService.getCurrentState();
            summary = state.getSummary();
        }
        return ResponseEntity.ok(summary);
    }
}

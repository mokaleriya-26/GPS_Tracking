package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRouteDTO {
    private String id;
    private String name;
    private String description;
    private String origin;
    private String destination;
    private BigDecimal estimatedDistanceKm;
    private int estimatedMinutes;
    private List<SimulationWaypointDTO> waypoints;
}

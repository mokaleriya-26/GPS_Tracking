package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationEventRequestDTO {
    private String eventType; // OVERSPEED, HARSH_BRAKING, HARSH_ACCELERATION, GPS_DISCONNECT, RESTORE_GPS, NORMAL
    private BigDecimal overrideSpeedKmph;
    private String eventId; // Unique event/action ID for request idempotency and retry protection
}

package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.entity.CostRecord;
import com.gps.tracking.service.CostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/costs")
@RequiredArgsConstructor
@Tag(name = "Costs", description = "Vehicle operational cost records")
public class CostController {
    private final CostService costService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CostRecord>>> getAll(
            @RequestParam(required = false) Long vehicleId) {
        if (vehicleId != null) return ResponseEntity.ok(ApiResponse.ok(costService.getByVehicle(vehicleId)));
        return ResponseEntity.ok(ApiResponse.ok(costService.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CostRecord>> create(@RequestBody CostRecord record) {
        return ResponseEntity.ok(ApiResponse.ok(costService.save(record), "Cost record created"));
    }
}

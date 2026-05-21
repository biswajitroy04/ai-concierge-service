package com.hotel.concierge.controller;

import com.hotel.concierge.dto.DashboardStats;
import com.hotel.concierge.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Admin dashboard endpoints")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats/{hotelId}")
    @Operation(summary = "Get dashboard statistics for a hotel")
    public ResponseEntity<DashboardStats> getStats(@PathVariable Long hotelId) {
        DashboardStats stats = dashboardService.getStats(hotelId);
        return ResponseEntity.ok(stats);
    }
}

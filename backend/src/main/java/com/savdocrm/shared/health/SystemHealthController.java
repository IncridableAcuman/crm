package com.savdocrm.shared.health;

import com.savdocrm.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "System diagnostic and status check endpoints")
public class SystemHealthController {

    @GetMapping
    @Operation(summary = "Check system status", description = "Returns active platform metadata and timestamp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealthStatus() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "system", "SAVDOCRM",
                "version", "0.0.1-SNAPSHOT",
                "serverTime", Instant.now().toString(),
                "framework", "Spring Boot 4.2.0 / Java 21"
        );
        return ResponseEntity.ok(ApiResponse.success(healthInfo));
    }
}

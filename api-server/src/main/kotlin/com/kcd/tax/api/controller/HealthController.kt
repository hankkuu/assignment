package com.kcd.tax.api.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): HealthResponse {
        return HealthResponse(
            status = "UP",
            timestamp = LocalDateTime.now()
        )
    }

    data class HealthResponse(
        val status: String,
        val timestamp: LocalDateTime
    )
}

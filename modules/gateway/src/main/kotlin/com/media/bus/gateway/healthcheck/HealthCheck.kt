package com.media.bus.gateway.healthcheck

import com.media.bus.common.web.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/gateway")
class HealthCheck {

    private val log = LoggerFactory.getLogger(HealthCheck::class.java)

    @GetMapping("/health-check")
    fun healthCheck(): ApiResponse<Unit?> {
        return ApiResponse.success()
    }
}

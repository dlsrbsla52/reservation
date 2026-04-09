package com.media.bus.iam.healthcheck

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthServiceHealthCheck {

    @GetMapping("/health-check")
    fun healthCheck(): ResponseEntity<Void> = ResponseEntity.ok().build()
}

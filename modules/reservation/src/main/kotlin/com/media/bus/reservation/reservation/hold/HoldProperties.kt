package com.media.bus.reservation.reservation.hold

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 선점(hold) 정책 설정 */
@ConfigurationProperties(prefix = "reservation.hold")
data class HoldProperties(
    val duration: Duration = Duration.ofDays(7),
    val maxActivePerMember: Int = 100,
    val gateTtl: Duration = Duration.ofSeconds(5),
    val occupiedCacheTtl: Duration = Duration.ofSeconds(30),
    val expiryBatchSize: Int = 100,
)

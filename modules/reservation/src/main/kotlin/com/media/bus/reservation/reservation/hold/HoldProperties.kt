package com.media.bus.reservation.reservation.hold

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * ## 선점(hold) 정책 설정
 *
 * 정책 값이 바뀌어도 코드를 고치지 않도록 `reservation.hold.*` 로 뺀다.
 *
 * @property duration            선점 유지 기간. 예약 시점과 상담 시작 시점에 이 기간만큼 만료가 설정된다.
 * @property maxActivePerMember  회원 1명이 동시에 가질 수 있는 진행 중 예약 수.
 * @property gateTtl             Redis 게이트 락 TTL. 예약 1건의 S2S 호출 + DB 저장이 끝나기에 충분한 시간.
 * @property occupiedCacheTtl    예약 성공 후 게이트 키를 "점유됨" 캐시로 유지하는 시간.
 * @property expiryBatchSize     만료 스케줄러가 한 번에 처리하는 예약 수.
 */
@ConfigurationProperties(prefix = "reservation.hold")
data class HoldProperties(
    val duration: Duration = Duration.ofDays(7),
    val maxActivePerMember: Int = 100,
    val gateTtl: Duration = Duration.ofSeconds(5),
    val occupiedCacheTtl: Duration = Duration.ofSeconds(30),
    val expiryBatchSize: Int = 100,
)

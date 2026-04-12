package com.media.bus.reservation.reservation.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 예약 상태 Enum
 *
 * 예약은 `reservation_consultation.status` 컬럼에 상태 이력을 누적 기록한다.
 * 현재 상태는 `reservation_consultation` 중 가장 최근(`created_at` 기준) row 의 상태로 판단한다.
 *
 * 상태 전이:
 * - `PENDING` → `CONSULTING` (관리자가 상담 시작)
 * - `CONSULTING` → `COMPLETED` (상담 종료 + 계약 연결)
 * - `PENDING` → `CANCELLED` (사용자 취소, 상담 전에만 허용)
 */
@Suppress("unused")
enum class ReservationStatus(
    override val displayName: String,
) : BaseEnum {
    PENDING("상담 대기"),
    CONSULTING("상담 중"),
    COMPLETED("상담 완료"),
    CANCELLED("취소됨"),
    ;

    /** 현재 상태에서 `target` 상태로 전이 가능한지 검사한다. */
    fun canTransitionTo(target: ReservationStatus): Boolean = when (this) {
        PENDING -> target == CONSULTING || target == CANCELLED
        CONSULTING -> target == COMPLETED
        COMPLETED, CANCELLED -> false
    }

    companion object {
        fun fromName(name: String): ReservationStatus? = BaseEnum.fromName<ReservationStatus>(name)
    }
}

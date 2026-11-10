package com.media.bus.reservation.reservation.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 예약 상태 Enum
 *
 * 현재 상태는 `reservation.status` 컬럼이 원본이고, `reservation_consultation` 은 변경 이력이다.
 *
 * 진행 중(`PENDING`, `CONSULTING`)인 예약은 정류소 광고면을 **선점**한다.
 * 종료 상태로 넘어가면 선점이 풀려 다른 회원이 예약할 수 있다.
 *
 * 상태 전이:
 * - `PENDING` → `CONSULTING` (관리자가 상담 시작)
 * - `PENDING` → `CANCELLED` (사용자 취소, 상담 전에만 허용)
 * - `PENDING` / `CONSULTING` → `REJECTED` (관리자 반려)
 * - `PENDING` / `CONSULTING` → `EXPIRED` (선점 기간 만료, 시스템만 수행)
 * - `CONSULTING` → `COMPLETED` (상담 종료 + 계약 연결)
 */
@Suppress("unused")
enum class ReservationStatus(
    override val displayName: String,
) : BaseEnum {
    PENDING("상담 대기"),
    CONSULTING("상담 중"),
    COMPLETED("상담 완료"),
    CANCELLED("취소됨"),
    REJECTED("반려됨"),
    EXPIRED("기간 만료"),
    ;

    /** 정류소 광고면을 선점하고 있는 상태인지. DB 부분 유니크 인덱스의 조건과 반드시 같아야 한다. */
    val holdsSlot: Boolean
        get() = this == PENDING || this == CONSULTING

    /** 현재 상태에서 `target` 상태로 전이 가능한지 검사한다. */
    fun canTransitionTo(target: ReservationStatus): Boolean = when (this) {
        PENDING -> target == CONSULTING || target == CANCELLED || target == REJECTED || target == EXPIRED
        CONSULTING -> target == COMPLETED || target == REJECTED || target == EXPIRED
        COMPLETED, CANCELLED, REJECTED, EXPIRED -> false
    }

    companion object {
        /** 선점 상태 목록. 쿼리 조건에 쓴다. */
        val HOLDING: List<ReservationStatus> = entries.filter { it.holdsSlot }

        fun fromName(name: String): ReservationStatus? = BaseEnum.fromName<ReservationStatus>(name)
    }
}

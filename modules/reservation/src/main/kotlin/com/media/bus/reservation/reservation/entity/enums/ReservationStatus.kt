package com.media.bus.reservation.reservation.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 예약 상태 Enum */
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

    val holdsSlot: Boolean
        get() = this == PENDING || this == CONSULTING

    /** 현재 상태에서 `target` 상태로 전이 가능한지 검사한다. */
    fun canTransitionTo(target: ReservationStatus): Boolean = when (this) {
        PENDING -> target == CONSULTING || target == CANCELLED || target == REJECTED || target == EXPIRED
        CONSULTING -> target == COMPLETED || target == REJECTED || target == EXPIRED
        COMPLETED, CANCELLED, REJECTED, EXPIRED -> false
    }

    companion object {
        val HOLDING: List<ReservationStatus> = entries.filter { it.holdsSlot }

        fun fromName(name: String): ReservationStatus? = BaseEnum.fromName<ReservationStatus>(name)
    }
}

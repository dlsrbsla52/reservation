package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 계약 상태 Enum
 *
 * - PENDING: 계약 대기 (초기 생성 시 기본값)
 * - ACTIVE: 계약 활성 (승인 완료)
 * - EXPIRED: 계약 만료 (계약 기간 종료)
 * - CANCELLED: 계약 취소
 *
 * `PENDING`, `ACTIVE` 계약은 계약 기간 동안 정류소 광고면을 **점유**한다.
 * 같은 광고면에서 점유 계약끼리는 기간이 겹칠 수 없다(DB 제외 제약 `ex_contract_slot_period`).
 */
@Suppress("unused")
enum class ContractStatus(
    override val displayName: String,
) : BaseEnum {
    PENDING("대기"),
    ACTIVE("활성"),
    EXPIRED("만료"),
    CANCELLED("취소됨"),
    ;

    /** 계약 기간 동안 광고면을 점유하는 상태인지. DB 제외 제약의 조건과 반드시 같아야 한다. */
    val occupiesSlot: Boolean
        get() = this == PENDING || this == ACTIVE

    companion object {
        /** 점유 상태 목록. 쿼리 조건에 쓴다. */
        val OCCUPYING: List<ContractStatus> = entries.filter { it.occupiesSlot }

        fun fromName(name: String): ContractStatus? = BaseEnum.fromName<ContractStatus>(name)
    }
}

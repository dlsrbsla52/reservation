package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 계약 상태 Enum */
@Suppress("unused")
enum class ContractStatus(
    override val displayName: String,
) : BaseEnum {
    PENDING("대기"),
    ACTIVE("활성"),
    EXPIRED("만료"),
    CANCELLED("취소됨"),
    ;

    val occupiesSlot: Boolean
        get() = this == PENDING || this == ACTIVE

    companion object {
        val OCCUPYING: List<ContractStatus> = entries.filter { it.occupiesSlot }

        fun fromName(name: String): ContractStatus? = BaseEnum.fromName<ContractStatus>(name)
    }
}

package com.media.bus.reservation.contract.dto.request

import com.media.bus.reservation.contract.entity.enums.ContractStatus
import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 검색 조건. 조회 쿼리와 count 쿼리가 같은 조건 객체를 공유한다. */
data class AdminContractSearchCondition(
    val memberId: UUID?,
    val stopId: UUID?,
    val status: ContractStatus?,
    val keyword: String?,
    val contractStartFrom: OffsetDateTime?,
    val contractStartTo: OffsetDateTime?,
)

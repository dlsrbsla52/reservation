package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.OffsetDateTime
import java.util.*

/** reservation 내부 계약 생성 응답의 IAM 표현. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ContractView(
    val id: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val memberId: UUID?,
    val contractName: String,
    val status: String,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

package com.media.bus.iam.client.reservation.dto

import com.media.bus.iam.admin.dto.CreateAdminContractRequest
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** reservation 내부 계약 생성 API 요청의 IAM 표현. */
data class CreateContractRequest(
    val stopId: UUID,
    val contractName: String,
    val totalAmount: BigDecimal,
    val payAmount: BigDecimal?,
    val paymentCycle: String,
    val paymentMethod: String,
    val memberId: UUID?,
    val note: String?,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
) {
    companion object {
        fun from(request: CreateAdminContractRequest) = CreateContractRequest(
            stopId = request.stopId,
            contractName = request.contractName,
            totalAmount = request.totalAmount,
            payAmount = request.payAmount,
            paymentCycle = request.paymentCycle,
            paymentMethod = request.paymentMethod,
            memberId = request.memberId,
            note = request.note,
            contractStartDate = request.contractStartDate,
            contractEndDate = request.contractEndDate,
        )
    }
}

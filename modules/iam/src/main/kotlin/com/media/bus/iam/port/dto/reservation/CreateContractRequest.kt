package com.media.bus.iam.port.dto.reservation

import com.media.bus.iam.admin.dto.CreateAdminContractRequest
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 생성 요청 (`ReservationAdminPort` 입력). 코드값은 문자열이고 enum 변환은 `app` 어댑터가 한다. */
data class CreateContractRequest(
    val stopId: UUID,
    val contractName: String,
    val totalAmount: BigDecimal?,
    val payAmount: BigDecimal?,
    val paymentCycle: String,
    val paymentMethod: String,
    val memberId: UUID?,
    val note: String?,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime?,
    val durationMonths: Int?,
    val managerId: UUID?,
) {
    companion object {
        fun from(request: CreateAdminContractRequest, managerId: UUID) = CreateContractRequest(
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
            durationMonths = request.durationMonths,
            managerId = managerId,
        )
    }
}

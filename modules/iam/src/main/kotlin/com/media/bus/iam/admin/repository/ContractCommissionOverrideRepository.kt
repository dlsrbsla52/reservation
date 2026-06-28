package com.media.bus.iam.admin.repository

import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import com.media.bus.iam.admin.entity.ContractCommissionOverrideTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 계약별 정산 비율 오버라이드 Repository
 */
@Repository
class ContractCommissionOverrideRepository {

    /** 계약 ID로 오버라이드 단건을 조회한다. */
    fun findByContractId(contractId: UUID): ContractCommissionOverrideEntity? =
        ContractCommissionOverrideEntity
            .find { ContractCommissionOverrideTable.contractId eq contractId }
            .firstOrNull()

    /**
     * 특정 영업사원의 모든 계약 오버라이드 목록을 최신순으로 조회한다.
     * 영업사원별 오버라이드 현황 조회에 사용한다.
     */
    fun findAllByMemberId(memberId: UUID): List<ContractCommissionOverrideEntity> =
        ContractCommissionOverrideEntity
            .find { ContractCommissionOverrideTable.memberId eq memberId }
            .orderBy(ContractCommissionOverrideTable.updatedAt to SortOrder.DESC)
            .toList()
}

package com.media.bus.iam.admin.repository

import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.CommissionRateHistoryTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/** 정산 비율 변경 이력 Repository */
@Repository
class CommissionRateHistoryRepository {

    /** 특정 영업사원의 정산 비율 변경 이력을 최신순으로 조회한다. */
    fun findAllByMemberId(memberId: UUID): List<CommissionRateHistoryEntity> =
        CommissionRateHistoryEntity
            .find { CommissionRateHistoryTable.memberId eq memberId }
            .orderBy(CommissionRateHistoryTable.createdAt to SortOrder.DESC)
            .toList()

    /** 특정 계약의 정산 비율 변경 이력을 최신순으로 조회한다. */
    fun findAllByContractId(contractId: UUID): List<CommissionRateHistoryEntity> =
        CommissionRateHistoryEntity
            .find { CommissionRateHistoryTable.contractId eq contractId }
            .orderBy(CommissionRateHistoryTable.createdAt to SortOrder.DESC)
            .toList()
}

package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractDetailTable
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 계약 상세 저장소 */
@Repository
class ContractDetailRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ContractDetailEntity? = ContractDetailEntity.findById(id)

    /** 복수 계약 ID 기반 일괄 조회. */
    @Transactional(readOnly = true)
    fun findByContractIds(contractIds: Collection<UUID>): List<ContractDetailEntity> {
        if (contractIds.isEmpty()) return emptyList()
        return ContractDetailEntity
            .find { ContractDetailTable.contractId inList contractIds.toList() }
            .toList()
    }
}

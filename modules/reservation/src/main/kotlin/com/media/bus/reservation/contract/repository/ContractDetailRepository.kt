package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractDetailTable
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 계약 상세 저장소
 *
 * Exposed DAO 기반 계약 상세 조회 메서드 제공.
 */
@Repository
class ContractDetailRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ContractDetailEntity? = ContractDetailEntity.findById(id)

    /**
     * 복수 계약 ID 기반 일괄 조회.
     * 어드민 계약 페이지 응답을 만들 때 N+1을 방지하기 위해 bulk 조회로 사용한다.
     * 빈 입력에 대해서는 DB 호출 없이 빈 리스트를 반환한다.
     */
    @Transactional(readOnly = true)
    fun findByContractIds(contractIds: Collection<UUID>): List<ContractDetailEntity> {
        if (contractIds.isEmpty()) return emptyList()
        return ContractDetailEntity
            .find { ContractDetailTable.contractId inList contractIds.toList() }
            .toList()
    }
}

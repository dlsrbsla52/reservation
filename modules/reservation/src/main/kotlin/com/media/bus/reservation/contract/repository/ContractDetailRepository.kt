package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.entity.ContractDetailEntity
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
}

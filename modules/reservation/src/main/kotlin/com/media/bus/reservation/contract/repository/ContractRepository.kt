package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.entity.ContractEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 계약 저장소
 *
 * Exposed DAO 기반 계약 조회 메서드 제공.
 * 계약 생성은 `ContractEntity.create()` 팩토리 메서드로 트랜잭션 내에서 자동 저장된다.
 */
@Repository
class ContractRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ContractEntity? = ContractEntity.findById(id)
}

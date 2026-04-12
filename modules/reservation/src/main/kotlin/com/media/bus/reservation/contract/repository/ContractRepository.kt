package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.entity.ContractTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
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

    /** 본인 소유 계약만 조회. 다른 회원 계약이면 null 을 반환하여 인가 검증에 사용한다. */
    @Transactional(readOnly = true)
    fun findByIdAndMemberId(id: UUID, memberId: UUID): ContractEntity? =
        ContractEntity.find {
            (ContractTable.id eq id) and (ContractTable.memberId eq memberId)
        }.firstOrNull()

    /** 회원 계약 총 건수. 페이지네이션 메타에 사용한다. */
    @Transactional(readOnly = true)
    fun countByMemberId(memberId: UUID): Long =
        ContractTable.selectAll().where { ContractTable.memberId eq memberId }.count()

    /** 회원의 계약 목록을 생성일 내림차순으로 페이지 조회한다. */
    @Transactional(readOnly = true)
    fun findByMemberIdPaged(memberId: UUID, page: Int, size: Int): List<ContractEntity> =
        ContractEntity.find { ContractTable.memberId eq memberId }
            .orderBy(ContractTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset(start = (page * size).toLong())
            .toList()
}

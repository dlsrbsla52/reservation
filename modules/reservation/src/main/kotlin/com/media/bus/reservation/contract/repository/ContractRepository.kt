package com.media.bus.reservation.contract.repository

import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.entity.ContractTable
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 계약 저장소
 *
 * Exposed DAO 기반 계약 조회 메서드 제공.
 * 계약 생성은 `ContractEntity.create()` 팩토리 메서드로 트랜잭션 내에서 자동 저장된다.
 */
@Repository
class ContractRepository {

    /** 관리자 검색 조건에 맞는 계약을 비회원 계약까지 포함해 최신 생성순으로 조회한다. */
    @Transactional(readOnly = true)
    fun searchAdminContracts(
        condition: AdminContractSearchCondition,
        page: Int,
        size: Int,
    ): List<ContractEntity> {
        val predicate = adminSearchPredicate(condition)
        return ContractTable.selectAll()
            .where { predicate }
            .orderBy(ContractTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset(start = (page * size).toLong())
            .map { ContractEntity.wrapRow(it) }
    }

    /** 목록 쿼리와 완전히 동일한 조건으로 전체 건수를 계산한다. */
    @Transactional(readOnly = true)
    fun countAdminContracts(condition: AdminContractSearchCondition): Long =
        ContractTable.selectAll().where { adminSearchPredicate(condition) }.count()

    private fun adminSearchPredicate(condition: AdminContractSearchCondition): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        condition.memberId?.let { conditions += ContractTable.memberId eq it }
        condition.stopId?.let { conditions += ContractTable.stopId eq it }
        condition.status?.let { conditions += ContractTable.status eq it }
        condition.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
            // 사용자 입력의 %, _를 와일드카드가 아닌 문자 그대로 검색한다.
            val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(it) + LikePattern("%", '\\')
            conditions += ContractTable.contractName like pattern
        }
        condition.contractStartFrom?.let { conditions += ContractTable.contractStartDate greaterEq it }
        condition.contractStartTo?.let { conditions += ContractTable.contractStartDate lessEq it }
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): ContractEntity? = ContractEntity.findById(id)

    /**
     * 광고면에 `[start, end)` 기간과 겹치는 점유 계약(PENDING/ACTIVE)이 있는지 확인한다.
     *
     * DB 제외 제약과 같은 반열림 구간 기준이다. 이 조회는 친절한 오류 응답을 위한 사전 검사이고,
     * 동시 요청 사이의 최종 판정은 제외 제약이 한다.
     */
    @Transactional(readOnly = true)
    fun existsOccupyingOverlap(stopId: UUID, slotNo: Short, start: OffsetDateTime, end: OffsetDateTime): Boolean =
        ContractTable.selectAll()
            .where {
                (ContractTable.stopId eq stopId) and
                    (ContractTable.slotNo eq slotNo) and
                    (ContractTable.status inList ContractStatus.OCCUPYING) and
                    (ContractTable.contractStartDate less end) and
                    (ContractTable.contractEndDate greater start)
            }
            .limit(1)
            .any()

    /** 특정 시점에 광고면을 점유 중인 계약이 있는지 확인한다. */
    @Transactional(readOnly = true)
    fun existsOccupyingAt(stopId: UUID, slotNo: Short, at: OffsetDateTime): Boolean =
        ContractTable.selectAll()
            .where {
                (ContractTable.stopId eq stopId) and
                    (ContractTable.slotNo eq slotNo) and
                    (ContractTable.status inList ContractStatus.OCCUPYING) and
                    (ContractTable.contractStartDate lessEq at) and
                    (ContractTable.contractEndDate greater at)
            }
            .limit(1)
            .any()

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

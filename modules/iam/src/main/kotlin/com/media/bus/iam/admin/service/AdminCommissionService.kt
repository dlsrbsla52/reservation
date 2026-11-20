package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import com.media.bus.iam.admin.repository.CommissionRateHistoryRepository
import com.media.bus.iam.admin.repository.ContractCommissionOverrideRepository
import com.media.bus.iam.admin.repository.ManagerCommissionRepository
import com.media.bus.iam.auth.result.AuthResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/** 영업사원 정산 비율 서비스 */
@Service
class AdminCommissionService(
    private val managerCommissionRepository: ManagerCommissionRepository,
    private val contractCommissionOverrideRepository: ContractCommissionOverrideRepository,
    private val commissionRateHistoryRepository: CommissionRateHistoryRepository,
) {

    /** 영업사원의 기본 정산 비율을 조회한다. 없으면 [AuthResult.COMMISSION_NOT_FOUND] 예외. */
    @Transactional(readOnly = true)
    fun getManagerCommission(memberId: UUID): ManagerCommissionEntity =
        managerCommissionRepository.findByMemberId(memberId)
            ?: throw BusinessException(AuthResult.COMMISSION_NOT_FOUND)

    /** 영업사원 기본 정산 비율을 초기화한다. */
    @Transactional
    fun initManagerCommission(memberId: UUID): ManagerCommissionEntity {
        if (managerCommissionRepository.existsByMemberId(memberId)) {
            throw BusinessException(AuthResult.COMMISSION_ALREADY_EXISTS)
        }
        val entity = ManagerCommissionEntity.create(memberId)
        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.DEFAULT_RATE,
            previousRate = null,
            newRate = entity.commissionRate,
            changedBy = memberId,
        )
        return entity
    }

    /** 영업사원의 기본 정산 비율을 변경하고 이력을 기록한다. */
    @Transactional
    fun updateManagerCommissionRate(
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ManagerCommissionEntity {
        val entity = managerCommissionRepository.findByMemberId(memberId)
            ?: throw BusinessException(AuthResult.COMMISSION_NOT_FOUND)

        val previousRate = entity.commissionRate
        entity.commissionRate = newRate
        entity.updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))

        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.DEFAULT_RATE,
            previousRate = previousRate,
            newRate = newRate,
            changedBy = changedBy,
            reason = reason,
        )
        return entity
    }

    /** 계약의 정산 비율 오버라이드를 조회한다. 없으면 null 반환. */
    @Transactional(readOnly = true)
    fun getContractCommissionOverride(contractId: UUID): ContractCommissionOverrideEntity? =
        contractCommissionOverrideRepository.findByContractId(contractId)

    /** 계약별 정산 비율 오버라이드를 등록하거나 수정하고 이력을 기록한다. */
    @Transactional
    fun upsertContractCommissionOverride(
        contractId: UUID,
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ContractCommissionOverrideEntity {
        val existing = contractCommissionOverrideRepository.findByContractId(contractId)
        val previousRate: BigDecimal?

        val entity = if (existing == null) {
            previousRate = null
            ContractCommissionOverrideEntity.create(
                contractId = contractId,
                memberId = memberId,
                commissionRate = newRate,
                updatedBy = changedBy,
            )
        } else {
            previousRate = existing.commissionRate
            existing.commissionRate = newRate
            existing.updatedBy = changedBy
            existing.updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
            existing
        }

        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.CONTRACT_OVERRIDE,
            previousRate = previousRate,
            newRate = newRate,
            changedBy = changedBy,
            contractId = contractId,
            reason = reason,
        )
        return entity
    }

    /** 계약에 적용할 최종 정산 비율을 결정한다. */
    @Transactional(readOnly = true)
    fun resolveEffectiveRate(memberId: UUID, contractId: UUID? = null): BigDecimal {
        if (contractId != null) {
            contractCommissionOverrideRepository.findByContractId(contractId)
                ?.let { return it.commissionRate }
        }
        return managerCommissionRepository.findByMemberId(memberId)?.commissionRate
            ?: DEFAULT_COMMISSION_RATE
    }

    /** 영업사원의 정산 비율 변경 이력 전체를 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    fun getCommissionHistory(memberId: UUID): List<CommissionRateHistoryEntity> =
        commissionRateHistoryRepository.findAllByMemberId(memberId)

    /** 특정 계약의 정산 비율 변경 이력을 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    fun getContractCommissionHistory(contractId: UUID): List<CommissionRateHistoryEntity> =
        commissionRateHistoryRepository.findAllByContractId(contractId)

    companion object {
        val DEFAULT_COMMISSION_RATE: BigDecimal = BigDecimal("10.00")
    }
}

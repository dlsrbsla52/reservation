package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * ## 계약별 정산 비율 오버라이드 테이블
 *
 * 스키마: `auth`, 테이블: `contract_commission_override`
 * 계약 당 1건. `contract_id` UNIQUE 제약으로 1:1 보장.
 * `contract_id`는 `reservation.contract.id`를, `member_id`는 `auth.member.id`를
 * 논리적으로 참조한다 (DB FK 미설정, 애플리케이션 레벨 관리).
 */
object ContractCommissionOverrideTable : DateBaseTable("auth.contract_commission_override") {
    /** 대상 계약 — reservation.contract.id 논리적 참조 */
    val contractId = javaUUID("contract_id").uniqueIndex("uq_contract_commission_override_contract_id")
    /** 계약 담당 영업사원 — auth.member.id 논리적 참조 (감사/조회 편의용 비정규화) */
    val memberId = javaUUID("member_id").index("idx_contract_commission_override_member_id")
    /** 오버라이드 정산 비율(%) */
    val commissionRate = decimal("commission_rate", 5, 2)
    /** 수정한 Master 회원 — auth.member.id 논리적 참조 */
    val updatedBy = javaUUID("updated_by")
}

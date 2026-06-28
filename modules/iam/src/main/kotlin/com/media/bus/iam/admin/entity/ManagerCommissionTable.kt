package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal

/**
 * ## 영업사원 기본 정산 비율 테이블
 *
 * 스키마: `auth`, 테이블: `manager_commission`
 * 영업사원(member) 당 1건. `member_id` UNIQUE 제약으로 1:1 보장.
 * `member_id`는 `auth.member.id`를 논리적으로 참조한다 (DB FK 미설정, 애플리케이션 레벨 관리).
 */
object ManagerCommissionTable : DateBaseTable("auth.manager_commission") {
    /** 대상 영업사원 — auth.member.id 논리적 참조 */
    val memberId = javaUUID("member_id").uniqueIndex("uq_manager_commission_member_id")
    /** 정산 비율(%) — 10.00 = 10%, 기본값 10% */
    val commissionRate = decimal("commission_rate", 5, 2).default(BigDecimal("10.00"))
}

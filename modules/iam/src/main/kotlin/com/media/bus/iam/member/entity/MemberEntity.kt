package com.media.bus.iam.member.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 회원 인증 정보 Exposed DAO 엔티티
 *
 * `MemberTable`에 매핑되는 DAO 엔티티.
 * 팩토리 메서드(`create`, `createAdmin`)로만 생성을 허용한다.
 */
class MemberEntity(id: EntityID<UUID>) : DateBaseEntity(id, MemberTable) {

    companion object : UUIDEntityClass<MemberEntity>(MemberTable) {

        /** 일반 회원 생성 팩토리 — 이메일 미인증 상태로 시작 */
        fun create(
            loginId: String,
            encodedPassword: String,
            email: String,
            phoneNumber: String,
            businessNumber: String?,
        ): MemberEntity = new(UuidV7.generate()) {
            this.loginId = loginId
            this.password = encodedPassword
            this.email = email
            this.phoneNumber = phoneNumber
            this.emailVerified = false
            this.status = MemberStatus.ACTIVE
            this.businessNumber = businessNumber
        }

        /** 어드민 회원 생성 팩토리 — 관리자가 직접 생성하므로 이메일 인증 완료 상태 */
        fun createAdmin(
            loginId: String,
            encodedPassword: String,
            email: String,
            phoneNumber: String,
        ): MemberEntity = new(UuidV7.generate()) {
            this.loginId = loginId
            this.password = encodedPassword
            this.email = email
            this.phoneNumber = phoneNumber
            this.emailVerified = true
            this.status = MemberStatus.ACTIVE
            this.businessNumber = null
        }
    }

    var loginId by MemberTable.loginId
    var password by MemberTable.password
    var email by MemberTable.email
    var phoneNumber by MemberTable.phoneNumber
    var emailVerified by MemberTable.emailVerified
    var status by MemberTable.status
    var businessNumber by MemberTable.businessNumber

    // ─────────────────────────────────────────────────────────────────
    // 도메인 행위 메서드
    // ─────────────────────────────────────────────────────────────────

    fun verifyEmail() {
        emailVerified = true
    }

    fun withdraw() {
        status = MemberStatus.WITHDRAWN
    }

    fun suspend() {
        status = MemberStatus.SUSPENDED
    }
}

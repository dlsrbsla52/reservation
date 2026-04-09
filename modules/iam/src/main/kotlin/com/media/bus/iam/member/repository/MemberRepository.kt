package com.media.bus.iam.member.repository

import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.MemberTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 회원 Exposed Repository
 *
 * 인증 목적의 조회 메서드를 제공한다.
 */
@Repository
class MemberRepository {

    fun findById(id: UUID): MemberEntity? = MemberEntity.findById(id)

    /** 로그인 아이디로 회원 조회. 로그인 및 중복 검사에 사용한다. */
    fun findByLoginId(loginId: String): MemberEntity? =
        MemberEntity.find { MemberTable.loginId eq loginId }.firstOrNull()

    /** 이메일로 회원 조회. 이메일 중복 검사 및 이메일 인증 처리에 사용한다. */
    fun findByEmail(email: String): MemberEntity? =
        MemberEntity.find { MemberTable.email eq email }.firstOrNull()

    /** 로그인 아이디 존재 여부 확인. 회원가입 시 중복 아이디 검증에 사용한다. */
    fun existsByLoginId(loginId: String): Boolean =
        MemberTable.selectAll().where { MemberTable.loginId eq loginId }.count() > 0

    /** 이메일 존재 여부 확인. 회원가입 시 중복 이메일 검증에 사용한다. */
    fun existsByEmail(email: String): Boolean =
        MemberTable.selectAll().where { MemberTable.email eq email }.count() > 0
}

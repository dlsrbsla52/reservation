package com.media.bus.iam.member.repository

import com.media.bus.iam.auth.entity.MemberRoleTable
import com.media.bus.iam.auth.entity.RoleTable
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.dto.MemberWithRole
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.MemberTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import java.util.*

/** 회원 Exposed Repository */
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

    /** 회원이 아이디를 분실했을 경우 회원 조회. */
    fun findMe(request: FindMeRequest): MemberEntity? =
        MemberTable.selectAll()
            .where {
                var condition = (MemberTable.memberName eq request.memberName) and
                    (MemberTable.status inList listOf(MemberStatus.ACTIVE, MemberStatus.INACTIVE))

                request.phoneNumber?.let {
                    condition = condition and (MemberTable.phoneNumber eq it)
                }
                request.email?.let {
                    condition = condition and (MemberTable.email eq it)
                }

                condition
            }
            .firstOrNull()
            ?.let { MemberEntity.wrapRow(it) }

    /** 전체 회원 수 조회 */
    fun count(): Long = MemberTable.selectAll().count()

    /** 오프셋 기반 페이지네이션으로 회원 목록 조회 (생성일 내림차순) */
    fun findAllPaged(page: Int, size: Int): List<MemberEntity> =
        MemberTable.selectAll()
            .orderBy(MemberTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset((page * size).toLong())
            .map { MemberEntity.wrapRow(it) }

    /** loginId, email, memberName 키워드 검색 (전방 일치 LIKE — 인덱스 활용). */
    fun searchByKeyword(keyword: String): List<MemberWithRole> {
        val pattern = startsWithPattern(keyword)
        return (MemberTable innerJoin MemberRoleTable innerJoin RoleTable)
            .selectAll()
            .where {
                (MemberTable.loginId like pattern) or
                    (MemberTable.email like pattern) or
                    (MemberTable.memberName like pattern)
            }
            .map { MemberWithRole(MemberEntity.wrapRow(it), it[RoleTable.name]) }
    }

    /** 조건 + 역할 필터로 회원을 페이지 조회한다. */
    fun searchByCondition(
        condition: MemberSearchCondition,
        roleNames: Set<String>,
    ): List<MemberWithRole> {
        if (roleNames.isEmpty()) return emptyList()

        return (MemberTable innerJoin MemberRoleTable innerJoin RoleTable)
            .selectAll()
            .where { buildSearchPredicate(condition, roleNames) }
            .orderBy(MemberTable.createdAt to SortOrder.DESC)
            .limit(condition.size)
            .offset((condition.page.toLong() * condition.size))
            .map { MemberWithRole(MemberEntity.wrapRow(it), it[RoleTable.name]) }
    }

    /** [searchByCondition] 와 동일 조건의 전체 건수. 페이지네이션 메타용. */
    fun countByCondition(
        condition: MemberSearchCondition,
        roleNames: Set<String>,
    ): Long {
        if (roleNames.isEmpty()) return 0L

        return (MemberTable innerJoin MemberRoleTable innerJoin RoleTable)
            .selectAll()
            .where { buildSearchPredicate(condition, roleNames) }
            .count()
    }

    /** 동적 검색 술어 빌더. */
    private fun buildSearchPredicate(
        condition: MemberSearchCondition,
        roleNames: Set<String>,
    ): Op<Boolean> {
        var predicate: Op<Boolean> = RoleTable.name inList roleNames

        condition.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { keyword ->
            val pattern = containsPattern(keyword)
            predicate = predicate and (
                (MemberTable.loginId like pattern) or
                    (MemberTable.email like pattern) or
                    (MemberTable.memberName like pattern)
                )
        }
        condition.status?.let { predicate = predicate and (MemberTable.status eq it) }
        condition.businessNumber?.let { predicate = predicate and (MemberTable.businessNumber eq it) }
        condition.createdFrom?.let { predicate = predicate and (MemberTable.createdAt greaterEq it) }
        condition.createdTo?.let { predicate = predicate and (MemberTable.createdAt lessEq it) }

        return predicate
    }

    /** 사용자 입력 키워드로 "부분 일치" LIKE 패턴을 만든다. */
    private fun containsPattern(keyword: String): LikePattern {
        val escaped = LikePattern.ofLiteral(keyword).pattern
        return LikePattern("%$escaped%", escapeChar = '\\')
    }

    /** 사용자 입력 키워드로 "전방 일치" LIKE 패턴을 만든다. */
    private fun startsWithPattern(keyword: String): LikePattern {
        val escaped = LikePattern.ofLiteral(keyword).pattern
        return LikePattern("$escaped%", escapeChar = '\\')
    }
}

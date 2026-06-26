package com.media.bus.iam.member.repository

import com.media.bus.iam.auth.entity.MemberRoleTable
import com.media.bus.iam.auth.entity.RoleTable
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.dto.MemberWithRole
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.MemberTable
import org.jetbrains.exposed.v1.core.*
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

    /**
     * 회원이 아이디를 분실했을 경우 회원 조회.
     * memberName은 필수, phoneNumber/email은 존재하는 것만 AND 조건으로 추가.
     */
    fun findMe(request: FindMeRequest): MemberEntity? =
        MemberTable.selectAll()
            .where {
                var condition = MemberTable.memberName eq request.memberName

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

    /**
     * loginId, email, memberName 키워드 검색 (전방 일치 LIKE — 인덱스 활용).
     * role JOIN으로 역할 이름을 함께 반환하여 N+1 없이 1쿼리로 처리한다.
     */
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

    // ─────────────────────────────────────────────────────────────────
    // 조건 기반 동적 검색 (일반/어드민 회원 공용)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 조건 + 역할 필터로 회원을 페이지 조회한다.
     *
     * **데이터 구조 제약**: 회원 유형(`MemberType`)은 `auth.member` 가 아니라
     * `auth.role`/`auth.member_role` 에 있으므로, 카테고리/유형 필터를 적용하려면
     * `member_role`·`role` 을 INNER JOIN 해야 한다. `member_id` 는 UNIQUE 이므로
     * 회원당 행이 1건으로 유지되어 중복은 발생하지 않는다.
     *
     * 역할 이름(`role.name` = `MemberType.name`)을 함께 반환하여, 호출 측에서
     * 행마다 다시 역할을 조회(N+1)하지 않고 곧바로 유형을 매핑하도록 한다.
     *
     * @param roleNames 허용된 역할 이름 집합. 비면 빈 결과를 반환한다(불필요한 쿼리 방지).
     * @return (회원 엔티티, 역할 이름) 쌍 목록 (가입일 내림차순)
     */
    fun searchByCondition(
        condition: MemberSearchCondition,
        roleNames: Set<String>,
    ): List<MemberWithRole> {
        // 빈 역할 집합이면 조회 자체가 무의미하므로 리포지토리 단독 호출에서도 방어한다.
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

    /**
     * 동적 검색 술어 빌더.
     * 역할 필터를 기본으로 깔고, 값이 존재하는 조건만 AND 로 누적한다.
     * Exposed 1.0 부터 비교 연산자(`eq`/`like` 등)가 top-level 함수이므로 별도 리시버 없이 작성한다.
     */
    private fun buildSearchPredicate(
        condition: MemberSearchCondition,
        roleNames: Set<String>,
    ): Op<Boolean> {
        var predicate: Op<Boolean> = RoleTable.name inList roleNames

        // 앞뒤 공백 제거 후 빈 문자열이면 키워드 조건을 적용하지 않는다.
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

    /**
     * 사용자 입력 키워드로 "부분 일치" LIKE 패턴을 만든다.
     *
     * 입력에 포함된 LIKE 메타문자(`%`, `_`, 이스케이프 문자)를 `LikePattern.ofLiteral`로
     * 이스케이프하여 과다 매칭/의도치 않은 패턴 해석을 방지한다(예: `100%` 검색이 전체 매칭이 되는 문제).
     * 이스케이프된 리터럴 양옆에 와일드카드(`%`)를 붙여 contains 검색으로 사용한다.
     */
    private fun containsPattern(keyword: String): LikePattern {
        val escaped = LikePattern.ofLiteral(keyword).pattern
        return LikePattern("%$escaped%", escapeChar = '\\')
    }

    /**
     * 사용자 입력 키워드로 "전방 일치" LIKE 패턴을 만든다.
     *
     * `keyword%` 형태로 앞 와일드카드가 없으므로 B-tree 인덱스를 활용할 수 있다.
     * [containsPattern]과 동일한 이스케이프 처리를 적용한다.
     */
    private fun startsWithPattern(keyword: String): LikePattern {
        val escaped = LikePattern.ofLiteral(keyword).pattern
        return LikePattern("$escaped%", escapeChar = '\\')
    }
}

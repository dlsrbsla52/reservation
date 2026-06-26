package com.media.bus.iam.admin.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.admin.dto.MemberSearchResponse
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ## 회원 조건 검색 공용 서비스
 *
 * 일반 회원 조회(`AdminUserMemberController`)와 어드민 회원 조회(`AdminMemberController`)가 공유한다.
 *
 * **권한 경계 강제**: 호출 컨트롤러가 허용 카테고리(`allowedCategories`)를 넘긴다.
 * 운영팀(일반 어드민)은 `USER`/`BUSINESS` 만, 마스터는 `ADMIN` 만 조회하도록
 * 카테고리를 고정함으로써, 검색 조건으로 대상 집단 경계를 넘는 것을 차단한다.
 */
@Service
class MemberSearchService(
    private val memberRepository: MemberRepository,
) {
    companion object {
        /** 페이지 크기 상한. 과대 요청으로 인한 메모리/부하(DoS 표면)를 막는다. */
        private const val MAX_PAGE_SIZE = 100
    }

    /**
     * 허용 카테고리 범위 내에서 조건에 맞는 회원을 페이지 조회한다.
     *
     * 1. 허용 카테고리 + 조건의 정밀 유형(`type`)을 교집합하여 검색 대상 역할 이름을 산출
     * 2. 산출된 역할이 없으면(예: 운영자가 ADMIN 유형을 지정) 빈 결과 반환 — 경계 위반 차단
     * 3. 회원 조회 + 전체 건수 조회 후 `PageResult` 로 래핑
     *
     * @param rawCondition 동적 검색 조건(페이지 정보 포함). page/size 는 내부에서 보정한다.
     * @param allowedCategories 이 호출에서 조회를 허용할 회원 카테고리 집합
     */
    @Transactional(readOnly = true)
    fun search(
        rawCondition: MemberSearchCondition,
        allowedCategories: Set<MemberCategory>,
    ): PageResult<MemberSearchResponse> {
        // 페이지 파라미터 보정: 음수 page 방지, size 상한으로 과대 요청(DoS 표면) 차단
        val condition = rawCondition.copy(
            page = rawCondition.page.coerceAtLeast(0),
            size = rawCondition.size.coerceIn(1, MAX_PAGE_SIZE),
        )

        // 허용 카테고리에 속하면서, 정밀 유형 조건이 있으면 그 유형까지 만족하는 역할 이름 집합
        val roleNames = MemberType.entries
            .filter { it.category in allowedCategories }
            .filter { condition.type == null || it == condition.type }
            .map { it.name }
            .toSet()

        // 조회 가능한 역할이 없으면 DB 조회 없이 빈 페이지 반환 (경계를 넘는 type 지정 등)
        if (roleNames.isEmpty()) {
            return PageResult(emptyList(), 0, condition.size, condition.page)
        }

        val rows = memberRepository.searchByCondition(condition, roleNames)
        val totalCnt = memberRepository.countByCondition(condition, roleNames)

        val items = rows.map {
            // roleName 은 role.name = MemberType.name 이므로 항상 매핑 가능
            val memberType = MemberType.fromName(it.roleName)
                ?: error("매핑 불가능한 역할 이름: ${it.roleName}")
            MemberSearchResponse.of(it.member, memberType)
        }

        return PageResult(
            items = items,
            totalCnt = totalCnt,
            pageRows = condition.size,
            pageNum = condition.page,
        )
    }
}

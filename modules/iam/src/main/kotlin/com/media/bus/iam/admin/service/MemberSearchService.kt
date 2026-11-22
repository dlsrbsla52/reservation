package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.ServiceException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.admin.dto.MemberSearchResponse
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원 조건 검색 공용 서비스 */
@Service
class MemberSearchService(
    private val memberRepository: MemberRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    /** 허용 카테고리 범위 내에서 조건에 맞는 회원을 페이지 조회한다. */
    @Transactional(readOnly = true)
    fun search(
        rawCondition: MemberSearchCondition,
        allowedCategories: Set<MemberCategory>,
    ): PageResult<MemberSearchResponse> {
        val condition = rawCondition.copy(
            page = rawCondition.page.coerceAtLeast(0),
            size = rawCondition.size.coerceIn(1, MAX_PAGE_SIZE),
        )

        val roleNames = MemberType.entries
            .filter { it.category in allowedCategories }
            .filter { condition.type == null || it == condition.type }
            .map { it.name }
            .toSet()

        if (roleNames.isEmpty()) {
            return PageResult(emptyList(), 0, condition.size, condition.page)
        }

        val rows = memberRepository.searchByCondition(condition, roleNames)
        val totalCnt = memberRepository.countByCondition(condition, roleNames)

        val items = rows.map {
            val memberType = MemberType.fromName(it.roleName)
                ?: run {
                    log.error("[MemberSearchService] 매핑 불가능한 역할 이름: {}", it.roleName)
                    throw ServiceException(AuthResult.UNKNOWN_ROLE_NAME)
                }
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

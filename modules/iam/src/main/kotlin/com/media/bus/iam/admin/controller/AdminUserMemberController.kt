package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.MemberSearchResponse
import com.media.bus.iam.admin.service.MemberSearchService
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * ## 일반 회원(USER/BUSINESS) 조회 컨트롤러
 *
 * 운영팀 등 일반 어드민(`ADMIN` 카테고리 + `READ` 권한)이 사용한다.
 * 어드민 계정 관리(`AdminMemberController`, `ADMIN_MASTER`+`MANAGE`)와 권한을 분리하여,
 * 승객/비즈니스 회원 조회에 마스터 권한을 요구하지 않도록 한다.
 *
 * 조회 대상은 `USER`/`BUSINESS` 카테고리로 고정한다(서비스에서 강제).
 * 향후 블랙리스트 등록, 회원별 예약 추적의 진입점이 된다.
 *
 * **예약 연계**: 회원별 예약/실적은 모듈 경계상 reservation 서비스가 별도 엔드포인트로 제공한다.
 * 여기서는 회원 정보만 반환한다.
 */
@Tag(name = "어드민 일반 회원 조회 API", description = "운영팀용 일반(USER/BUSINESS) 회원 조건 검색. ADMIN 카테고리 + READ 권한 필요.")
@RestController
@RequestMapping("/api/v1/admin")
@Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.READ])
class AdminUserMemberController(
    private val memberSearchService: MemberSearchService,
) {
    /** 일반 회원을 조건으로 검색한다. 조회 대상은 USER/BUSINESS 로 고정된다. */
    @Operation(
        summary = "일반 회원 조건 검색",
        description = "키워드/상태/유형/사업자번호/가입일 범위로 일반(USER·BUSINESS) 회원을 페이지 조회합니다.",
    )
    @GetMapping("/users/search")
    fun searchUserMembers(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: MemberStatus?,
        @RequestParam(required = false) type: MemberType?,
        @RequestParam(required = false) businessNumber: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdFrom: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdTo: OffsetDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<MemberSearchResponse>> {
        val condition = MemberSearchCondition(
            keyword = keyword,
            status = status,
            type = type,
            businessNumber = businessNumber,
            createdFrom = createdFrom,
            createdTo = createdTo,
            page = page,
            size = size,
        )
        // 일반 회원만 조회 가능하도록 USER/BUSINESS 카테고리로 고정
        return ApiResponse.success(
            memberSearchService.search(condition, setOf(MemberCategory.USER, MemberCategory.BUSINESS)),
        )
    }
}

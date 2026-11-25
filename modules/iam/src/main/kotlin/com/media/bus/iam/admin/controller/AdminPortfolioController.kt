package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.port.ContentAdminPort
import com.media.bus.iam.port.dto.content.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/** 관리자 포트폴리오 관리 */
@Tag(name = "어드민 포트폴리오", description = "홈페이지 포트폴리오 관리. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE")
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/content/portfolio")
class AdminPortfolioController(private val contentAdminPort: ContentAdminPort) {

    @Operation(summary = "포트폴리오 목록", description = "숨긴 항목을 포함해 정렬 순서대로 조회한다.")
    @GetMapping
    fun list(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminPortfolio>> = ApiResponse.success(contentAdminPort.getPortfolios(category, keyword, page, size))

    @Operation(summary = "포트폴리오 상세")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ApiResponse<AdminPortfolio> = ApiResponse.success(contentAdminPort.getPortfolio(id))

    @Operation(summary = "포트폴리오 등록", description = "업로드를 마친 이미지 키로 등록한다. 새 항목은 목록 맨 앞에 온다.")
    @PostMapping
    fun create(@RequestBody @Valid request: AdminPortfolioRequest): ApiResponse<AdminPortfolio> =
        ApiResponse.success(contentAdminPort.createPortfolio(request))

    @Operation(summary = "포트폴리오 수정", description = "이미지를 바꾸면 이전 이미지는 저장소에서 지운다.")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: AdminPortfolioRequest): ApiResponse<AdminPortfolio> =
        ApiResponse.success(contentAdminPort.updatePortfolio(id, request))

    @Operation(summary = "포트폴리오 삭제", description = "이미지도 저장소에서 지운다.")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ApiResponse<Unit?> = ApiResponse.successWith { contentAdminPort.deletePortfolio(id) }

    @Operation(summary = "포트폴리오 순서 변경", description = "화면에 보일 순서대로 전체 ID를 보낸다.")
    @PutMapping("/order")
    fun reorder(@RequestBody @Valid request: AdminSortOrderRequest): ApiResponse<Unit?> =
        ApiResponse.successWith { contentAdminPort.reorderPortfolios(request.ids) }
}

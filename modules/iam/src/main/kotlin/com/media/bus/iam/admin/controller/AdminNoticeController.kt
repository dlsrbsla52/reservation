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

/** 관리자 공지사항 관리 */
@Tag(name = "어드민 공지사항", description = "홈페이지 공지사항 관리. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE")
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/content/notice")
class AdminNoticeController(private val contentAdminPort: ContentAdminPort) {

    @Operation(summary = "공지사항 목록", description = "숨긴 글을 포함한다. searchType은 TITLE(기본)·BODY.")
    @GetMapping
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) searchType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminNoticeSummary>> = ApiResponse.success(contentAdminPort.getNotices(keyword, searchType, page, size))

    @Operation(summary = "공지사항 상세", description = "조회수를 올리지 않는다.")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ApiResponse<AdminNotice> = ApiResponse.success(contentAdminPort.getNotice(id))

    @Operation(summary = "공지사항 등록")
    @PostMapping
    fun create(@RequestBody @Valid request: AdminNoticeRequest): ApiResponse<AdminNotice> =
        ApiResponse.success(contentAdminPort.createNotice(request))

    @Operation(summary = "공지사항 수정", description = "목록에서 빠진 이미지는 저장소에서 지운다.")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: AdminNoticeRequest): ApiResponse<AdminNotice> =
        ApiResponse.success(contentAdminPort.updateNotice(id, request))

    @Operation(summary = "공지사항 삭제")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ApiResponse<Unit?> = ApiResponse.successWith { contentAdminPort.deleteNotice(id) }
}

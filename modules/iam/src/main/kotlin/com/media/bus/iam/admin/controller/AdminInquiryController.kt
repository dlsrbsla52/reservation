package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.iam.port.ContentAdminPort
import com.media.bus.iam.port.dto.content.AdminInquiry
import com.media.bus.iam.port.dto.content.AdminInquiryStatusRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/** 관리자 온라인 문의 처리 */
@Tag(name = "어드민 온라인 문의", description = "홈페이지 온라인 문의 목록·처리. 조회는 READ, 상태 변경·삭제는 WRITE 권한")
@RestController
@Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.READ])
@RequestMapping("/api/v1/admin/content/inquiry")
class AdminInquiryController(private val contentAdminPort: ContentAdminPort) {

    @Operation(summary = "온라인 문의 목록", description = "접수일 역순. status(RECEIVED·DONE)로 거른다.")
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminInquiry>> = ApiResponse.success(contentAdminPort.getInquiries(status, page, size))

    @Operation(summary = "온라인 문의 상세")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ApiResponse<AdminInquiry> = ApiResponse.success(contentAdminPort.getInquiry(id))

    @Operation(
        summary = "문의 처리 상태 변경",
        description = "처리 상태를 저장하고 처리한 관리자를 기록한다. memo가 null이면 기존 메모를 유지하고 빈 문자열이면 지운다. 상담 완료는 status=DONE이다.",
    )
    @Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.WRITE])
    @PutMapping("/{id}/status")
    fun changeStatus(
        @CurrentMember member: MemberPrincipal,
        @PathVariable id: UUID,
        @RequestBody @Valid request: AdminInquiryStatusRequest,
    ): ApiResponse<AdminInquiry> = ApiResponse.success(contentAdminPort.changeInquiryStatus(id, request, member.id))

    @Operation(summary = "온라인 문의 삭제", description = "문의를 영구 삭제한다. 처리 완료 후 보관 기간이 지난 문의는 서버가 매일 자동 파기한다.")
    @Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.WRITE])
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ApiResponse<Unit?> = ApiResponse.successWith { contentAdminPort.deleteInquiry(id) }
}

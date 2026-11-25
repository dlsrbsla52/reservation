package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
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

/** 관리자 FAQ 관리 */
@Tag(name = "어드민 FAQ", description = "홈페이지 FAQ 관리. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE")
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/content/faq")
class AdminFaqController(private val contentAdminPort: ContentAdminPort) {

    @Operation(summary = "FAQ 전체", description = "숨긴 항목을 포함해 정렬 순서대로 조회한다.")
    @GetMapping
    fun list(): ApiResponse<List<AdminFaq>> = ApiResponse.success(contentAdminPort.getFaqs())

    @Operation(summary = "FAQ 등록", description = "목록 맨 뒤에 추가한다.")
    @PostMapping
    fun create(@RequestBody @Valid request: AdminFaqRequest): ApiResponse<AdminFaq> = ApiResponse.success(contentAdminPort.createFaq(request))

    @Operation(summary = "FAQ 수정")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: AdminFaqRequest): ApiResponse<AdminFaq> =
        ApiResponse.success(contentAdminPort.updateFaq(id, request))

    @Operation(summary = "FAQ 삭제")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ApiResponse<Unit?> = ApiResponse.successWith { contentAdminPort.deleteFaq(id) }

    @Operation(summary = "FAQ 순서 변경", description = "화면에 보일 순서대로 전체 ID를 보낸다.")
    @PutMapping("/order")
    fun reorder(@RequestBody @Valid request: AdminSortOrderRequest): ApiResponse<Unit?> =
        ApiResponse.successWith { contentAdminPort.reorderFaqs(request.ids) }
}

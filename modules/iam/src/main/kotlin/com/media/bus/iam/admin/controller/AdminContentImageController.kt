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

/** 관리자 콘텐츠 이미지 업로드 URL 발급 */
@Tag(name = "어드민 콘텐츠 이미지", description = "포트폴리오·공지사항 이미지 업로드. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE")
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/content/images")
class AdminContentImageController(private val contentAdminPort: ContentAdminPort) {

    @Operation(summary = "이미지 업로드 URL 발급", description = "브라우저가 저장소(R2)로 직접 PUT 할 presigned URL을 발급한다.")
    @PostMapping("/upload-url")
    fun issueUploadUrl(@RequestBody @Valid request: AdminImageUploadRequest): ApiResponse<AdminImageUploadTicket> =
        ApiResponse.success(contentAdminPort.issueImageUpload(request))
}

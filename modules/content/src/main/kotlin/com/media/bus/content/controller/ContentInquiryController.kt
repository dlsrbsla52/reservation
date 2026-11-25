package com.media.bus.content.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.content.dto.InquiryCreateRequest
import com.media.bus.content.service.InquiryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공개 온라인 문의 API */
@Tag(name = "홈페이지 온라인 문의 API", description = "로그인 없이 문의를 접수한다. IP별 호출 제한이 있다.")
@RestController
@RequestMapping("/api/v1/content/inquiry")
class ContentInquiryController(private val inquiryService: InquiryService) {

    @Operation(summary = "온라인 문의 접수", description = "개인정보 수집·이용 동의(privacyAgreed=true)가 있어야 접수된다.")
    @PostMapping
    fun create(@RequestBody @Valid request: InquiryCreateRequest, servletRequest: HttpServletRequest): ApiResponse<Unit?> {
        inquiryService.create(request, servletRequest.remoteAddr)
        return ApiResponse.successWithMessage("문의가 접수되었습니다.")
    }
}

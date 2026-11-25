package com.media.bus.content.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.content.dto.FaqResponse
import com.media.bus.content.service.FaqService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 공개 FAQ API */
@Tag(name = "홈페이지 FAQ API", description = "로그인 없이 조회하는 공개 FAQ")
@RestController
@RequestMapping("/api/v1/content/faq")
class ContentFaqController(private val faqService: FaqService) {

    @Operation(summary = "FAQ 전체", description = "노출 중인 FAQ를 정렬 순서대로 모두 조회한다.")
    @GetMapping
    fun list(): ApiResponse<List<FaqResponse>> = ApiResponse.success(faqService.getPublicList().map(FaqResponse::of))
}

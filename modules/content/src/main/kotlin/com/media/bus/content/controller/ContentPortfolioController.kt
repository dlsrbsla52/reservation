package com.media.bus.content.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.PortfolioResponse
import com.media.bus.content.service.PortfolioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

/** 공개 포트폴리오 API */
@Tag(name = "홈페이지 포트폴리오 API", description = "로그인 없이 조회하는 공개 포트폴리오")
@RestController
@RequestMapping("/api/v1/content/portfolio")
class ContentPortfolioController(private val portfolioService: PortfolioService) {

    @Operation(summary = "포트폴리오 목록", description = "노출 중인 포트폴리오를 정렬 순서대로 조회한다. category·keyword(제목)로 거른다.")
    @GetMapping
    fun list(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
    ): ApiResponse<PageResult<PortfolioResponse>> {
        val result = portfolioService.getPublicPage(category, keyword, page, size)
        return ApiResponse.success(PageResult(result.items.map(PortfolioResponse::of), result.totalCnt, result.pageRows, result.pageNum))
    }

    @Operation(summary = "포트폴리오 상세")
    @GetMapping("/detail")
    fun detail(@RequestParam id: UUID): ApiResponse<PortfolioResponse> =
        ApiResponse.success(PortfolioResponse.of(portfolioService.getPublic(id)))
}

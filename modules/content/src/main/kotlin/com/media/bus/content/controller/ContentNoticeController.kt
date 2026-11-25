package com.media.bus.content.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.NoticeDetailResponse
import com.media.bus.content.dto.NoticeSummaryResponse
import com.media.bus.content.service.NoticeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

/** 공개 공지사항 API */
@Tag(name = "홈페이지 공지사항 API", description = "로그인 없이 조회하는 공개 공지사항")
@RestController
@RequestMapping("/api/v1/content/notice")
class ContentNoticeController(private val noticeService: NoticeService) {

    @Operation(summary = "공지사항 목록", description = "상단 고정 글이 먼저 나온다. searchType은 TITLE(기본)·BODY.")
    @GetMapping
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) searchType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<PageResult<NoticeSummaryResponse>> {
        val result = noticeService.getPublicPage(keyword, searchType, page, size)
        return ApiResponse.success(PageResult(result.items.map(NoticeSummaryResponse::of), result.totalCnt, result.pageRows, result.pageNum))
    }

    @Operation(summary = "공지사항 상세", description = "조회할 때마다 조회수가 1 오른다.")
    @GetMapping("/detail")
    fun detail(@RequestParam id: UUID): ApiResponse<NoticeDetailResponse> =
        ApiResponse.success(NoticeDetailResponse.of(noticeService.readPublic(id)))
}

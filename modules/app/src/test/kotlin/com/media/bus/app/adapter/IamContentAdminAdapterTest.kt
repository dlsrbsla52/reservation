package com.media.bus.app.adapter

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.ContentImageView
import com.media.bus.content.dto.InquiryStatusCommand
import com.media.bus.content.dto.InquiryView
import com.media.bus.content.dto.NoticeCommand
import com.media.bus.content.dto.NoticeView
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.dto.PortfolioView
import com.media.bus.content.entity.enums.InquiryStatus
import com.media.bus.content.entity.enums.PortfolioCategory
import com.media.bus.content.result.ContentResult
import com.media.bus.content.service.ContentImageService
import com.media.bus.content.service.FaqService
import com.media.bus.content.service.InquiryService
import com.media.bus.content.service.NoticeService
import com.media.bus.content.service.PortfolioService
import com.media.bus.iam.port.dto.content.AdminInquiryStatusRequest
import com.media.bus.iam.port.dto.content.AdminNoticeRequest
import com.media.bus.iam.port.dto.content.AdminPortfolioRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.*

/** iam → content 어댑터 계약 테스트 */
@DisplayName("IamContentAdminAdapter")
class IamContentAdminAdapterTest {

    private val contentImageService = mockk<ContentImageService>()
    private val portfolioService = mockk<PortfolioService>()
    private val noticeService = mockk<NoticeService>()
    private val faqService = mockk<FaqService>()
    private val inquiryService = mockk<InquiryService>()
    private val adapter = IamContentAdminAdapter(contentImageService, portfolioService, noticeService, faqService, inquiryService)

    private val now = OffsetDateTime.parse("2026-11-25T10:00:00+09:00")

    private fun portfolioView() = PortfolioView(
        id = UUID.randomUUID(), title = "오목교역", category = PortfolioCategory.FNB, description = null,
        imageKey = "portfolio/a.webp", thumbnailKey = "portfolio/a_thumb.webp", imageUrl = "https://img/a.webp",
        thumbnailUrl = "https://img/a_thumb.webp", sortOrder = 3, visible = false, legacyIdx = 118, createdAt = now, updatedAt = now,
    )

    @Test
    fun `포트폴리오 요청은 모든 필드를 넘기고 응답 분류는 enum 이름 문자열로 바꾼다`() {
        val command = slot<PortfolioCommand>()
        every { portfolioService.create(capture(command)) } returns portfolioView()

        val result = adapter.createPortfolio(
            AdminPortfolioRequest("오목교역", "FNB", "설명", "portfolio/a.webp", "portfolio/a_thumb.webp", false, 118),
        )

        assertThat(command.captured).isEqualTo(PortfolioCommand("오목교역", "FNB", "설명", "portfolio/a.webp", "portfolio/a_thumb.webp", false, 118))
        assertThat(result.category).isEqualTo("FNB")
        assertThat(result.visible).isFalse()
        assertThat(result.legacyIdx).isEqualTo(118)
    }

    @Test
    fun `생략한 노출 여부와 고정 여부는 기본값으로 채운다`() {
        val portfolio = slot<PortfolioCommand>()
        val notice = slot<NoticeCommand>()
        every { portfolioService.create(capture(portfolio)) } returns portfolioView()
        every { noticeService.create(capture(notice)) } throws BusinessException(ContentResult.NOTICE_NOT_FOUND)

        adapter.createPortfolio(AdminPortfolioRequest("t", "ETC", null, "portfolio/a.webp", "portfolio/a_thumb.webp"))
        assertThrows<BusinessException> { adapter.createNotice(AdminNoticeRequest("t", "b")) }

        assertThat(portfolio.captured.visible).isTrue()
        assertThat(notice.captured.pinned).isFalse()
        assertThat(notice.captured.visible).isTrue()
    }

    @Test
    fun `목록 페이지 메타는 그대로 옮긴다`() {
        every { portfolioService.getAdminPage(null, null, 2, 20) } returns PageResult(listOf(portfolioView()), 41, 20, 2)

        val page = adapter.getPortfolios(null, null, 2, 20)

        assertThat(page.totalCnt).isEqualTo(41)
        assertThat(page.pageNum).isEqualTo(2)
        assertThat(page.items).hasSize(1)
    }

    @Test
    fun `공지 요청의 이관 필드와 이미지 순서를 넘기고 응답 이미지를 키와 주소로 옮긴다`() {
        val command = slot<NoticeCommand>()
        every { noticeService.create(capture(command)) } returns NoticeView(
            id = UUID.randomUUID(), title = "t", body = "b", pinned = true, visible = true, viewCount = 52, publishedAt = now,
            images = listOf(ContentImageView("notice/1.webp", "https://img/1.webp")), legacyIdx = 18, createdAt = now, updatedAt = now,
        )

        val result = adapter.createNotice(AdminNoticeRequest("t", "b", true, true, now, listOf("notice/1.webp"), 18, 51))

        assertThat(command.captured.legacyViewCount).isEqualTo(51)
        assertThat(command.captured.imageKeys).containsExactly("notice/1.webp")
        assertThat(result.images.single().url).isEqualTo("https://img/1.webp")
    }

    @Test
    fun `문의 처리 상태 변경은 처리자를 넘기고 상태를 문자열로 돌려준다`() {
        val adminId = UUID.randomUUID()
        val id = UUID.randomUUID()
        every { inquiryService.changeStatus(id, InquiryStatusCommand("DONE", "완료"), adminId) } returns InquiryView(
            id = id, name = "n", phone = "010", email = "e@x.com", content = "c", status = InquiryStatus.DONE, adminMemo = "완료",
            handledById = adminId, handledAt = now, clientIp = null, createdAt = now,
        )

        val result = adapter.changeInquiryStatus(id, AdminInquiryStatusRequest("DONE", "완료"), adminId)

        assertThat(result.status).isEqualTo("DONE")
        assertThat(result.handledById).isEqualTo(adminId)
    }

    @Test
    fun `공지 이미지 키를 생략하면 빈 목록으로 넘긴다`() {
        val command = slot<NoticeCommand>()
        every { noticeService.create(capture(command)) } throws BusinessException(ContentResult.NOTICE_NOT_FOUND)

        assertThrows<BusinessException> { adapter.createNotice(AdminNoticeRequest("t", "b")) }

        assertThat(command.captured.imageKeys).isEmpty()
    }

    @Test
    fun `문의 메모를 생략하면 null 그대로 넘겨 기존 메모를 유지하게 한다`() {
        val adminId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val command = slot<InquiryStatusCommand>()
        every { inquiryService.changeStatus(id, capture(command), adminId) } throws BusinessException(ContentResult.INQUIRY_NOT_FOUND)

        assertThrows<BusinessException> { adapter.changeInquiryStatus(id, AdminInquiryStatusRequest("DONE"), adminId) }

        assertThat(command.captured.memo).isNull()
    }

    @Test
    fun `문의 삭제는 content 서비스로 위임하고 업무 예외를 그대로 전달한다`() {
        val id = UUID.randomUUID()
        every { inquiryService.delete(id) } throws BusinessException(ContentResult.INQUIRY_NOT_FOUND)

        val ex = assertThrows<BusinessException> { adapter.deleteInquiry(id) }

        assertThat(ex.result).isEqualTo(ContentResult.INQUIRY_NOT_FOUND)
    }

    @Test
    fun `콘텐츠 모듈의 4xx 업무 예외는 그대로 전달한다`() {
        every { portfolioService.get(any()) } throws BusinessException(ContentResult.PORTFOLIO_NOT_FOUND)

        val ex = assertThrows<BusinessException> { adapter.getPortfolio(UUID.randomUUID()) }

        assertThat(ex.result).isEqualTo(ContentResult.PORTFOLIO_NOT_FOUND)
        assertThat(ex.httpStatus).isEqualTo(HttpStatus.NOT_FOUND)
    }
}

package com.media.bus.app.adapter

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.FaqCommand
import com.media.bus.content.dto.FaqView
import com.media.bus.content.dto.ImageUploadCommand
import com.media.bus.content.dto.InquiryStatusCommand
import com.media.bus.content.dto.InquiryView
import com.media.bus.content.dto.NoticeCommand
import com.media.bus.content.dto.NoticeSummaryView
import com.media.bus.content.dto.NoticeView
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.dto.PortfolioView
import com.media.bus.content.service.ContentImageService
import com.media.bus.content.service.FaqService
import com.media.bus.content.service.InquiryService
import com.media.bus.content.service.NoticeService
import com.media.bus.content.service.PortfolioService
import com.media.bus.iam.port.ContentAdminPort
import com.media.bus.iam.port.dto.content.*
import org.springframework.stereotype.Component
import java.util.*

/** iam 관리자 기능 → content 어댑터 */
@Component
class IamContentAdminAdapter(
    private val contentImageService: ContentImageService,
    private val portfolioService: PortfolioService,
    private val noticeService: NoticeService,
    private val faqService: FaqService,
    private val inquiryService: InquiryService,
) : ContentAdminPort {

    override fun issueImageUpload(request: AdminImageUploadRequest): AdminImageUploadTicket = callProvider {
        val ticket = contentImageService.issueUpload(
            ImageUploadCommand(request.purpose, request.contentType, request.contentLength, request.thumbnail ?: false),
        )
        AdminImageUploadTicket(ticket.key, ticket.uploadUrl, ticket.headers, ticket.publicUrl, ticket.expiresAt)
    }

    override fun getPortfolios(category: String?, keyword: String?, page: Int, size: Int): PageResult<AdminPortfolio> =
        callProvider { portfolioService.getAdminPage(category, keyword, page, size).mapItems(::toAdminPortfolio) }

    override fun getPortfolio(id: UUID): AdminPortfolio = callProvider { toAdminPortfolio(portfolioService.get(id)) }

    override fun createPortfolio(request: AdminPortfolioRequest): AdminPortfolio =
        callProvider { toAdminPortfolio(portfolioService.create(toPortfolioCommand(request))) }

    override fun updatePortfolio(id: UUID, request: AdminPortfolioRequest): AdminPortfolio =
        callProvider { toAdminPortfolio(portfolioService.update(id, toPortfolioCommand(request))) }

    override fun deletePortfolio(id: UUID) = callProvider { portfolioService.delete(id) }

    override fun reorderPortfolios(ids: List<UUID>) = callProvider { portfolioService.reorder(ids) }

    override fun getNotices(keyword: String?, searchType: String?, page: Int, size: Int): PageResult<AdminNoticeSummary> =
        callProvider { noticeService.getAdminPage(keyword, searchType, page, size).mapItems(::toAdminNoticeSummary) }

    override fun getNotice(id: UUID): AdminNotice = callProvider { toAdminNotice(noticeService.get(id)) }

    override fun createNotice(request: AdminNoticeRequest): AdminNotice =
        callProvider { toAdminNotice(noticeService.create(toNoticeCommand(request))) }

    override fun updateNotice(id: UUID, request: AdminNoticeRequest): AdminNotice =
        callProvider { toAdminNotice(noticeService.update(id, toNoticeCommand(request))) }

    override fun deleteNotice(id: UUID) = callProvider { noticeService.delete(id) }

    override fun getFaqs(): List<AdminFaq> = callProvider { faqService.getAdminList().map(::toAdminFaq) }

    override fun createFaq(request: AdminFaqRequest): AdminFaq = callProvider { toAdminFaq(faqService.create(toFaqCommand(request))) }

    override fun updateFaq(id: UUID, request: AdminFaqRequest): AdminFaq =
        callProvider { toAdminFaq(faqService.update(id, toFaqCommand(request))) }

    override fun deleteFaq(id: UUID) = callProvider { faqService.delete(id) }

    override fun reorderFaqs(ids: List<UUID>) = callProvider { faqService.reorder(ids) }

    override fun getInquiries(status: String?, page: Int, size: Int): PageResult<AdminInquiry> =
        callProvider { inquiryService.getAdminPage(status, page, size).mapItems(::toAdminInquiry) }

    override fun getInquiry(id: UUID): AdminInquiry = callProvider { toAdminInquiry(inquiryService.get(id)) }

    override fun changeInquiryStatus(id: UUID, request: AdminInquiryStatusRequest, adminId: UUID): AdminInquiry =
        callProvider { toAdminInquiry(inquiryService.changeStatus(id, InquiryStatusCommand(request.status, request.memo), adminId)) }

    override fun deleteInquiry(id: UUID) = callProvider { inquiryService.delete(id) }

    private fun <T, R> PageResult<T>.mapItems(transform: (T) -> R): PageResult<R> =
        PageResult(items = items.map(transform), totalCnt = totalCnt, pageRows = pageRows, pageNum = pageNum)

    private fun toPortfolioCommand(request: AdminPortfolioRequest) = PortfolioCommand(
        title = request.title,
        category = request.category,
        description = request.description,
        imageKey = request.imageKey,
        thumbnailKey = request.thumbnailKey,
        visible = request.visible ?: true,
        legacyIdx = request.legacyIdx,
    )

    private fun toAdminPortfolio(view: PortfolioView) = AdminPortfolio(
        id = view.id,
        title = view.title,
        category = view.category.name,
        description = view.description,
        imageKey = view.imageKey,
        thumbnailKey = view.thumbnailKey,
        imageUrl = view.imageUrl,
        thumbnailUrl = view.thumbnailUrl,
        sortOrder = view.sortOrder,
        visible = view.visible,
        legacyIdx = view.legacyIdx,
        createdAt = view.createdAt,
        updatedAt = view.updatedAt,
    )

    private fun toNoticeCommand(request: AdminNoticeRequest) = NoticeCommand(
        title = request.title,
        body = request.body,
        pinned = request.pinned ?: false,
        visible = request.visible ?: true,
        publishedAt = request.publishedAt,
        imageKeys = request.imageKeys ?: emptyList(),
        legacyIdx = request.legacyIdx,
        legacyViewCount = request.legacyViewCount,
    )

    private fun toAdminNoticeSummary(view: NoticeSummaryView) = AdminNoticeSummary(
        id = view.id,
        title = view.title,
        pinned = view.pinned,
        visible = view.visible,
        viewCount = view.viewCount,
        publishedAt = view.publishedAt,
    )

    private fun toAdminNotice(view: NoticeView) = AdminNotice(
        id = view.id,
        title = view.title,
        body = view.body,
        pinned = view.pinned,
        visible = view.visible,
        viewCount = view.viewCount,
        publishedAt = view.publishedAt,
        images = view.images.map { AdminNoticeImage(it.key, it.url) },
        legacyIdx = view.legacyIdx,
        createdAt = view.createdAt,
        updatedAt = view.updatedAt,
    )

    private fun toFaqCommand(request: AdminFaqRequest) = FaqCommand(
        category = request.category,
        question = request.question,
        answer = request.answer,
        visible = request.visible ?: true,
    )

    private fun toAdminFaq(view: FaqView) = AdminFaq(
        id = view.id,
        category = view.category.name,
        question = view.question,
        answer = view.answer,
        sortOrder = view.sortOrder,
        visible = view.visible,
        createdAt = view.createdAt,
        updatedAt = view.updatedAt,
    )

    private fun toAdminInquiry(view: InquiryView) = AdminInquiry(
        id = view.id,
        name = view.name,
        phone = view.phone,
        email = view.email,
        content = view.content,
        status = view.status.name,
        adminMemo = view.adminMemo,
        handledById = view.handledById,
        handledAt = view.handledAt,
        clientIp = view.clientIp,
        createdAt = view.createdAt,
    )
}

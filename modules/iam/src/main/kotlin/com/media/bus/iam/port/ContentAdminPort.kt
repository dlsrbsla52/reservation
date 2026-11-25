package com.media.bus.iam.port

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.dto.content.*
import java.util.*

/** 관리자 기능이 쓰는 content 모듈 포트 */
interface ContentAdminPort {

    /** 이미지 업로드용 presigned URL을 발급한다. */
    fun issueImageUpload(request: AdminImageUploadRequest): AdminImageUploadTicket

    fun getPortfolios(category: String?, keyword: String?, page: Int, size: Int): PageResult<AdminPortfolio>

    fun getPortfolio(id: UUID): AdminPortfolio

    /** 같은 legacyIdx가 있으면 새로 만들지 않고 기존 항목을 돌려준다. */
    fun createPortfolio(request: AdminPortfolioRequest): AdminPortfolio

    fun updatePortfolio(id: UUID, request: AdminPortfolioRequest): AdminPortfolio

    fun deletePortfolio(id: UUID)

    fun reorderPortfolios(ids: List<UUID>)

    fun getNotices(keyword: String?, searchType: String?, page: Int, size: Int): PageResult<AdminNoticeSummary>

    fun getNotice(id: UUID): AdminNotice

    fun createNotice(request: AdminNoticeRequest): AdminNotice

    fun updateNotice(id: UUID, request: AdminNoticeRequest): AdminNotice

    fun deleteNotice(id: UUID)

    fun getFaqs(): List<AdminFaq>

    fun createFaq(request: AdminFaqRequest): AdminFaq

    fun updateFaq(id: UUID, request: AdminFaqRequest): AdminFaq

    fun deleteFaq(id: UUID)

    fun reorderFaqs(ids: List<UUID>)

    fun getInquiries(status: String?, page: Int, size: Int): PageResult<AdminInquiry>

    fun getInquiry(id: UUID): AdminInquiry

    fun changeInquiryStatus(id: UUID, request: AdminInquiryStatusRequest, adminId: UUID): AdminInquiry

    fun deleteInquiry(id: UUID)
}

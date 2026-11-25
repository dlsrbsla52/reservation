package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.InquiryCreateRequest
import com.media.bus.content.dto.InquiryStatusCommand
import com.media.bus.content.dto.InquiryView
import com.media.bus.content.entity.InquiryEntity
import com.media.bus.content.entity.enums.InquiryStatus
import com.media.bus.content.guard.InquiryRateLimiter
import com.media.bus.content.repository.InquiryRepository
import com.media.bus.content.result.ContentResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/** 온라인 문의 접수·처리 */
@Service
class InquiryService(
    private val inquiryRepository: InquiryRepository,
    private val inquiryRateLimiter: InquiryRateLimiter,
) {

    /** 공개 문의를 접수한다. 개인정보 동의가 없거나 같은 IP의 호출이 많으면 거절한다. */
    fun create(request: InquiryCreateRequest, clientIp: String?) {
        InputValidator.validate(request)
        if (request.privacyAgreed != true) throw BusinessException(ContentResult.PRIVACY_CONSENT_REQUIRED)
        inquiryRateLimiter.check(clientIp)
        inquiryRepository.create(request, clientIp)
    }

    @Transactional(readOnly = true)
    fun getAdminPage(status: String?, page: Int, size: Int): PageResult<InquiryView> {
        ContentPaging.normalize(page, size, null)
        val parsed = status?.takeIf { it.isNotBlank() }?.let(::parseStatus)
        return PageResult(
            items = inquiryRepository.findPage(parsed, page, size).map(::toView),
            totalCnt = inquiryRepository.count(parsed),
            pageRows = size,
            pageNum = page,
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): InquiryView = toView(find(id))

    /** 처리 상태를 바꾸고 처리한 관리자를 기록한다. 메모는 null이면 유지하고 빈 문자열이면 지운다. */
    @Transactional
    fun changeStatus(id: UUID, command: InquiryStatusCommand, adminId: UUID): InquiryView {
        InputValidator.validate(command)
        val entity = find(id)
        entity.changeStatus(parseStatus(command.status), command.memo, adminId)
        return toView(entity)
    }

    /** 문의를 지운다. */
    @Transactional
    fun delete(id: UUID) {
        find(id).delete()
    }

    /** 처리 완료 후 기준 시각이 지난 문의를 지우고 건수를 돌려준다. */
    @Transactional
    fun purgeHandledBefore(cutoff: OffsetDateTime): Int = inquiryRepository.deleteHandledBefore(cutoff)

    private fun find(id: UUID): InquiryEntity =
        inquiryRepository.findById(id) ?: throw BusinessException(ContentResult.INQUIRY_NOT_FOUND)

    private fun parseStatus(value: String): InquiryStatus =
        InquiryStatus.fromName(value) ?: throw BusinessException(ContentResult.INVALID_INQUIRY_STATUS)

    private fun toView(entity: InquiryEntity) = InquiryView(
        id = entity.id.value,
        name = entity.name,
        phone = entity.phone,
        email = entity.email,
        content = entity.content,
        status = entity.status,
        adminMemo = entity.adminMemo,
        handledById = entity.handledById,
        handledAt = entity.handledAt,
        clientIp = entity.clientIp,
        createdAt = entity.createdAt,
    )
}

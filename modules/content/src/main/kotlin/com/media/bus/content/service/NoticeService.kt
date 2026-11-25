package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.NoticeCommand
import com.media.bus.content.dto.NoticeSummaryView
import com.media.bus.content.dto.NoticeView
import com.media.bus.content.entity.NoticeEntity
import com.media.bus.content.entity.enums.ImagePurpose
import com.media.bus.content.entity.enums.NoticeSearchType
import com.media.bus.content.repository.NoticeRepository
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 공지사항 조회·관리 */
@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val contentImageService: ContentImageService,
    private val noticeWriter: NoticeWriter,
    private val storage: ContentStorage,
) {

    /** 공개 목록. 상단 고정 글이 먼저, 그다음 게시일 역순이다. */
    @Transactional(readOnly = true)
    fun getPublicPage(keyword: String?, searchType: String?, page: Int, size: Int): PageResult<NoticeSummaryView> =
        page(keyword, searchType, visibleOnly = true, page, size)

    /** 공개 상세. 조회할 때마다 조회수를 1 올린다. */
    @Transactional
    fun readPublic(id: UUID): NoticeView {
        val entity = noticeRepository.findById(id)?.takeIf { it.visible }
            ?: throw BusinessException(ContentResult.NOTICE_NOT_FOUND)
        noticeRepository.incrementViewCount(id)
        return toView(entity).let { it.copy(viewCount = it.viewCount + 1) }
    }

    @Transactional(readOnly = true)
    fun getAdminPage(keyword: String?, searchType: String?, page: Int, size: Int): PageResult<NoticeSummaryView> =
        page(keyword, searchType, visibleOnly = false, page, size)

    @Transactional(readOnly = true)
    fun get(id: UUID): NoticeView = toView(find(id))

    /** 공지사항을 등록한다. 같은 이관 식별자가 있으면 기존 글을 돌려준다. */
    fun create(command: NoticeCommand): NoticeView {
        InputValidator.validate(command)
        command.legacyIdx?.let { idx -> noticeWriter.findByLegacyIdx(idx)?.let { return it } }
        val keys = distinctKeys(command)
        contentImageService.verifyUploaded(ImagePurpose.NOTICE, keys)
        return try {
            noticeWriter.insert(command, keys)
        } catch (e: Exception) {
            command.legacyIdx?.let { noticeWriter.findByLegacyIdx(it) } ?: throw e
        }
    }

    /** 내용과 이미지 목록을 바꾼다. 빠진 이미지는 커밋 후 저장소에서 지운다. */
    fun update(id: UUID, command: NoticeCommand): NoticeView {
        InputValidator.validate(command)
        val previous = noticeWriter.currentImageKeys(id)
        val keys = distinctKeys(command)
        contentImageService.verifyUploaded(ImagePurpose.NOTICE, keys - previous)
        return noticeWriter.apply(id, command, keys)
    }

    @Transactional
    fun delete(id: UUID) {
        val entity = find(id)
        val keys = noticeRepository.findImages(id).map { it.imageKey }
        noticeRepository.deleteImages(id)
        entity.delete()
        storage.deleteAfterCommit(keys)
    }

    private fun page(keyword: String?, searchType: String?, visibleOnly: Boolean, page: Int, size: Int): PageResult<NoticeSummaryView> {
        val kw = ContentPaging.normalize(page, size, keyword)
        val type = searchType?.takeIf { it.isNotBlank() }
            ?.let { value -> NoticeSearchType.fromName(value) ?: throw BusinessException(ContentResult.INVALID_SEARCH_TYPE) }
            ?: NoticeSearchType.TITLE
        return PageResult(
            items = noticeRepository.findPage(kw, type, visibleOnly, page, size).map(::toSummary),
            totalCnt = noticeRepository.count(kw, type, visibleOnly),
            pageRows = size,
            pageNum = page,
        )
    }

    private fun distinctKeys(command: NoticeCommand): List<String> {
        val keys = command.imageKeys.distinct()
        if (keys.size > MAX_IMAGES) throw BusinessException(ContentResult.TOO_MANY_NOTICE_IMAGES)
        return keys
    }

    private fun find(id: UUID): NoticeEntity =
        noticeRepository.findById(id) ?: throw BusinessException(ContentResult.NOTICE_NOT_FOUND)

    private fun toSummary(entity: NoticeEntity) = NoticeSummaryView(
        id = entity.id.value,
        title = entity.title,
        pinned = entity.pinned,
        visible = entity.visible,
        viewCount = entity.viewCount,
        publishedAt = entity.publishedAt,
    )

    private fun toView(entity: NoticeEntity) = entity.toView(noticeRepository.findImages(entity.id.value), storage)

    companion object {
        private const val MAX_IMAGES = 10
    }
}

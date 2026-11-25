package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.dto.ContentImageView
import com.media.bus.content.dto.NoticeCommand
import com.media.bus.content.dto.NoticeView
import com.media.bus.content.entity.NoticeEntity
import com.media.bus.content.entity.NoticeImageEntity
import com.media.bus.content.repository.ContentImageRepository
import com.media.bus.content.repository.NoticeRepository
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 공지사항 쓰기 트랜잭션 경계. 저장소 확인 같은 외부 호출은 이 클래스를 부르기 전에 끝낸다. */
@Component
class NoticeWriter(
    private val noticeRepository: NoticeRepository,
    private val contentImageRepository: ContentImageRepository,
    private val storage: ContentStorage,
) {

    @Transactional(readOnly = true)
    fun findByLegacyIdx(legacyIdx: Int): NoticeView? =
        noticeRepository.findByLegacyIdx(legacyIdx)?.let { it.toView(noticeRepository.findImages(it.id.value), storage) }

    /** 지금 연결된 이미지 키. 공지가 없으면 404. */
    @Transactional(readOnly = true)
    fun currentImageKeys(id: UUID): Set<String> {
        find(id)
        return noticeRepository.findImages(id).map { it.imageKey }.toSet()
    }

    @Transactional
    fun insert(command: NoticeCommand, keys: List<String>): NoticeView {
        requireKeysFree(keys, null)
        val entity = NoticeEntity.create(command)
        entity.flush()
        keys.forEachIndexed { index, key -> NoticeImageEntity.create(entity.id.value, key, index) }
        return entity.toView(noticeRepository.findImages(entity.id.value), storage)
    }

    /** 내용과 이미지 목록을 바꾸고 빠진 이미지는 커밋 후 삭제 대상으로 넘긴다. */
    @Transactional
    fun apply(id: UUID, command: NoticeCommand, keys: List<String>): NoticeView {
        val entity = find(id)
        requireKeysFree(keys, id)
        val previous = noticeRepository.findImages(id).map { it.imageKey }
        entity.update(command)
        noticeRepository.deleteImages(id)
        keys.forEachIndexed { index, key -> NoticeImageEntity.create(id, key, index) }
        storage.deleteAfterCommit(previous - keys.toSet())
        return entity.toView(noticeRepository.findImages(id), storage)
    }

    private fun requireKeysFree(keys: List<String>, exceptId: UUID?) {
        if (contentImageRepository.isInUse(keys, exceptNoticeId = exceptId)) throw BusinessException(ContentResult.IMAGE_KEY_IN_USE)
    }

    private fun find(id: UUID): NoticeEntity =
        noticeRepository.findById(id) ?: throw BusinessException(ContentResult.NOTICE_NOT_FOUND)
}

internal fun NoticeEntity.toView(images: List<NoticeImageEntity>, storage: ContentStorage) = NoticeView(
    id = id.value,
    title = title,
    body = body,
    pinned = pinned,
    visible = visible,
    viewCount = viewCount,
    publishedAt = publishedAt,
    images = images.map { ContentImageView(it.imageKey, storage.publicUrl(it.imageKey)) },
    legacyIdx = legacyIdx,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

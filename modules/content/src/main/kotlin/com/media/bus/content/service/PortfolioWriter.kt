package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.dto.PortfolioView
import com.media.bus.content.entity.PortfolioEntity
import com.media.bus.content.entity.enums.PortfolioCategory
import com.media.bus.content.repository.ContentImageRepository
import com.media.bus.content.repository.PortfolioRepository
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 포트폴리오 쓰기 트랜잭션 경계. 저장소 확인 같은 외부 호출은 이 클래스를 부르기 전에 끝낸다. */
@Component
class PortfolioWriter(
    private val portfolioRepository: PortfolioRepository,
    private val contentImageRepository: ContentImageRepository,
    private val storage: ContentStorage,
) {

    @Transactional(readOnly = true)
    fun findByLegacyIdx(legacyIdx: Int): PortfolioView? = portfolioRepository.findByLegacyIdx(legacyIdx)?.toView(storage)

    /** 지금 저장된 원본·썸네일 키. 항목이 없으면 404. */
    @Transactional(readOnly = true)
    fun currentKeys(id: UUID): Set<String> = find(id).let { setOf(it.imageKey, it.thumbnailKey) }

    @Transactional
    fun insert(command: PortfolioCommand, category: PortfolioCategory): PortfolioView {
        requireKeysFree(command, null)
        val entity = PortfolioEntity.create(command, category, portfolioRepository.maxSortOrder() + 1)
        entity.flush()
        return entity.toView(storage)
    }

    /** 내용을 바꾸고 더 이상 쓰지 않는 이미지만 커밋 후 삭제 대상으로 넘긴다. */
    @Transactional
    fun apply(id: UUID, command: PortfolioCommand, category: PortfolioCategory): PortfolioView {
        val entity = find(id)
        requireKeysFree(command, id)
        storage.deleteAfterCommit(entity.update(command, category))
        return entity.toView(storage)
    }

    private fun requireKeysFree(command: PortfolioCommand, exceptId: UUID?) {
        if (contentImageRepository.isInUse(setOf(command.imageKey, command.thumbnailKey), exceptPortfolioId = exceptId)) {
            throw BusinessException(ContentResult.IMAGE_KEY_IN_USE)
        }
    }

    private fun find(id: UUID): PortfolioEntity =
        portfolioRepository.findById(id) ?: throw BusinessException(ContentResult.PORTFOLIO_NOT_FOUND)
}

internal fun PortfolioEntity.toView(storage: ContentStorage) = PortfolioView(
    id = id.value,
    title = title,
    category = category,
    description = description,
    imageKey = imageKey,
    thumbnailKey = thumbnailKey,
    imageUrl = storage.publicUrl(imageKey),
    thumbnailUrl = storage.publicUrl(thumbnailKey),
    sortOrder = sortOrder,
    visible = visible,
    legacyIdx = legacyIdx,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

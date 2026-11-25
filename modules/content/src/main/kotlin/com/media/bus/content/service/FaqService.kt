package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.content.dto.FaqCommand
import com.media.bus.content.dto.FaqView
import com.media.bus.content.entity.FaqEntity
import com.media.bus.content.entity.enums.FaqCategory
import com.media.bus.content.repository.FaqRepository
import com.media.bus.content.result.ContentResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** FAQ 조회·관리 */
@Service
class FaqService(private val faqRepository: FaqRepository) {

    /** 공개 FAQ. 노출 중인 항목을 정렬 순서대로 돌려준다. */
    @Transactional(readOnly = true)
    fun getPublicList(): List<FaqView> = faqRepository.findAll(visibleOnly = true).map(::toView)

    @Transactional(readOnly = true)
    fun getAdminList(): List<FaqView> = faqRepository.findAll(visibleOnly = false).map(::toView)

    /** 목록 맨 뒤에 등록한다. */
    @Transactional
    fun create(command: FaqCommand): FaqView {
        InputValidator.validate(command)
        return toView(FaqEntity.create(command, parseCategory(command.category), faqRepository.maxSortOrder() + 1))
    }

    @Transactional
    fun update(id: UUID, command: FaqCommand): FaqView {
        InputValidator.validate(command)
        val entity = find(id)
        entity.update(command, parseCategory(command.category))
        return toView(entity)
    }

    @Transactional
    fun delete(id: UUID) = find(id).delete()

    /** 화면에 보일 순서대로 받은 전체 ID로 정렬 순서를 다시 매긴다. */
    @Transactional
    fun reorder(ids: List<UUID>) {
        val all = faqRepository.findAll(visibleOnly = false).associateBy { it.id.value }
        ContentPaging.requireSameSet(ids, all.keys)
        ids.forEachIndexed { index, id -> all.getValue(id).sortOrder = index + 1 }
    }

    private fun find(id: UUID): FaqEntity = faqRepository.findById(id) ?: throw BusinessException(ContentResult.FAQ_NOT_FOUND)

    private fun parseCategory(value: String): FaqCategory =
        FaqCategory.fromName(value) ?: throw BusinessException(ContentResult.INVALID_CATEGORY)

    private fun toView(entity: FaqEntity) = FaqView(
        id = entity.id.value,
        category = entity.category,
        question = entity.question,
        answer = entity.answer,
        sortOrder = entity.sortOrder,
        visible = entity.visible,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )
}

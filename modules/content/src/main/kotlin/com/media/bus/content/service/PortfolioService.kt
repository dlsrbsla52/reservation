package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.dto.PortfolioView
import com.media.bus.content.entity.PortfolioEntity
import com.media.bus.content.entity.enums.ImagePurpose
import com.media.bus.content.entity.enums.PortfolioCategory
import com.media.bus.content.repository.PortfolioRepository
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 포트폴리오 조회·관리 */
@Service
class PortfolioService(
    private val portfolioRepository: PortfolioRepository,
    private val contentImageService: ContentImageService,
    private val portfolioWriter: PortfolioWriter,
    private val storage: ContentStorage,
) {

    /** 공개 목록. 노출 중인 항목만 정렬 순서대로 돌려준다. */
    @Transactional(readOnly = true)
    fun getPublicPage(category: String?, keyword: String?, page: Int, size: Int): PageResult<PortfolioView> =
        page(category, keyword, visibleOnly = true, page, size)

    /** 공개 상세. 숨긴 항목은 없는 것으로 본다. */
    @Transactional(readOnly = true)
    fun getPublic(id: UUID): PortfolioView {
        val entity = portfolioRepository.findById(id)?.takeIf { it.visible }
            ?: throw BusinessException(ContentResult.PORTFOLIO_NOT_FOUND)
        return toView(entity)
    }

    /** 관리자 목록. 숨긴 항목도 포함한다. */
    @Transactional(readOnly = true)
    fun getAdminPage(category: String?, keyword: String?, page: Int, size: Int): PageResult<PortfolioView> =
        page(category, keyword, visibleOnly = false, page, size)

    @Transactional(readOnly = true)
    fun get(id: UUID): PortfolioView = toView(find(id))

    /** 목록 맨 앞에 등록한다. 같은 이관 식별자가 있으면 새로 만들지 않고 기존 항목을 돌려준다. */
    fun create(command: PortfolioCommand): PortfolioView {
        InputValidator.validate(command)
        command.legacyIdx?.let { idx -> portfolioWriter.findByLegacyIdx(idx)?.let { return it } }
        val category = parseCategory(command.category)
        contentImageService.verifyUploaded(ImagePurpose.PORTFOLIO, setOf(command.imageKey, command.thumbnailKey))
        return try {
            portfolioWriter.insert(command, category)
        } catch (e: Exception) {
            command.legacyIdx?.let { portfolioWriter.findByLegacyIdx(it) } ?: throw e
        }
    }

    /** 내용을 수정한다. 교체된 이미지는 커밋 후 저장소에서 지운다. */
    fun update(id: UUID, command: PortfolioCommand): PortfolioView {
        InputValidator.validate(command)
        val current = portfolioWriter.currentKeys(id)
        val category = parseCategory(command.category)
        contentImageService.verifyUploaded(ImagePurpose.PORTFOLIO, setOf(command.imageKey, command.thumbnailKey) - current)
        return portfolioWriter.apply(id, command, category)
    }

    @Transactional
    fun delete(id: UUID) {
        val entity = find(id)
        val keys = listOf(entity.imageKey, entity.thumbnailKey)
        entity.delete()
        storage.deleteAfterCommit(keys)
    }

    /** 화면에 보일 순서(앞쪽이 먼저)대로 받은 전체 ID로 정렬 순서를 다시 매긴다. */
    @Transactional
    fun reorder(ids: List<UUID>) {
        val all = portfolioRepository.findAll().associateBy { it.id.value }
        ContentPaging.requireSameSet(ids, all.keys)
        ids.forEachIndexed { index, id -> all.getValue(id).sortOrder = ids.size - index }
    }

    private fun page(category: String?, keyword: String?, visibleOnly: Boolean, page: Int, size: Int): PageResult<PortfolioView> {
        val kw = ContentPaging.normalize(page, size, keyword)
        val parsed = category?.takeIf { it.isNotBlank() }?.let(::parseCategory)
        return PageResult(
            items = portfolioRepository.findPage(parsed, kw, visibleOnly, page, size).map(::toView),
            totalCnt = portfolioRepository.count(parsed, kw, visibleOnly),
            pageRows = size,
            pageNum = page,
        )
    }

    private fun find(id: UUID): PortfolioEntity =
        portfolioRepository.findById(id) ?: throw BusinessException(ContentResult.PORTFOLIO_NOT_FOUND)

    private fun parseCategory(value: String): PortfolioCategory =
        PortfolioCategory.fromName(value) ?: throw BusinessException(ContentResult.INVALID_CATEGORY)

    private fun toView(entity: PortfolioEntity) = entity.toView(storage)
}

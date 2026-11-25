package com.media.bus.content.repository

import com.media.bus.content.entity.NoticeEntity
import com.media.bus.content.entity.NoticeImageEntity
import com.media.bus.content.entity.NoticeImageTable
import com.media.bus.content.entity.NoticeTable
import com.media.bus.content.entity.enums.NoticeSearchType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class NoticeRepository {

    @Transactional(readOnly = true)
    fun findPage(keyword: String?, searchType: NoticeSearchType, visibleOnly: Boolean, page: Int, size: Int): List<NoticeEntity> =
        NoticeTable.selectAll()
            .where { predicate(keyword, searchType, visibleOnly) }
            .orderBy(
                NoticeTable.pinned to SortOrder.DESC,
                NoticeTable.publishedAt to SortOrder.DESC,
                NoticeTable.id to SortOrder.DESC,
            )
            .limit(size)
            .offset(page.toLong() * size)
            .map { NoticeEntity.wrapRow(it) }

    @Transactional(readOnly = true)
    fun count(keyword: String?, searchType: NoticeSearchType, visibleOnly: Boolean): Long =
        NoticeTable.selectAll().where { predicate(keyword, searchType, visibleOnly) }.count()

    @Transactional(readOnly = true)
    fun findById(id: UUID): NoticeEntity? = NoticeEntity.findById(id)

    @Transactional(readOnly = true)
    fun findByLegacyIdx(legacyIdx: Int): NoticeEntity? =
        NoticeEntity.find { NoticeTable.legacyIdx eq legacyIdx }.firstOrNull()

    @Transactional(readOnly = true)
    fun findImages(noticeId: UUID): List<NoticeImageEntity> =
        NoticeImageEntity.find { NoticeImageTable.noticeId eq noticeId }
            .orderBy(NoticeImageTable.sortOrder to SortOrder.ASC)
            .toList()

    @Transactional
    fun deleteImages(noticeId: UUID) {
        NoticeImageTable.deleteWhere { NoticeImageTable.noticeId eq noticeId }
    }

    @Transactional
    fun incrementViewCount(id: UUID) {
        NoticeTable.update({ NoticeTable.id eq id }) { it[viewCount] = viewCount + 1 }
    }

    private fun predicate(keyword: String?, searchType: NoticeSearchType, visibleOnly: Boolean): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        keyword?.let {
            val pattern = "%${ContentQueries.escapeLike(it)}%"
            conditions += when (searchType) {
                NoticeSearchType.TITLE -> NoticeTable.title like pattern
                NoticeSearchType.BODY -> NoticeTable.body like pattern
            }
        }
        if (visibleOnly) conditions += NoticeTable.visible eq true
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }
}

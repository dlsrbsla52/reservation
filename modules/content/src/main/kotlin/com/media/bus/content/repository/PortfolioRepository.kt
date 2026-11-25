package com.media.bus.content.repository

import com.media.bus.content.entity.PortfolioEntity
import com.media.bus.content.entity.PortfolioTable
import com.media.bus.content.entity.enums.PortfolioCategory
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class PortfolioRepository {

    @Transactional(readOnly = true)
    fun findPage(category: PortfolioCategory?, keyword: String?, visibleOnly: Boolean, page: Int, size: Int): List<PortfolioEntity> =
        PortfolioTable.selectAll()
            .where { predicate(category, keyword, visibleOnly) }
            .orderBy(
                PortfolioTable.sortOrder to SortOrder.DESC,
                PortfolioTable.createdAt to SortOrder.DESC,
                PortfolioTable.id to SortOrder.DESC,
            )
            .limit(size)
            .offset(page.toLong() * size)
            .map { PortfolioEntity.wrapRow(it) }

    @Transactional(readOnly = true)
    fun count(category: PortfolioCategory?, keyword: String?, visibleOnly: Boolean): Long =
        PortfolioTable.selectAll().where { predicate(category, keyword, visibleOnly) }.count()

    @Transactional(readOnly = true)
    fun findById(id: UUID): PortfolioEntity? = PortfolioEntity.findById(id)

    @Transactional(readOnly = true)
    fun findByLegacyIdx(legacyIdx: Int): PortfolioEntity? =
        PortfolioEntity.find { PortfolioTable.legacyIdx eq legacyIdx }.firstOrNull()

    /** 정렬 순서 최댓값. 비어 있으면 0. */
    @Transactional(readOnly = true)
    fun maxSortOrder(): Int {
        val max = PortfolioTable.sortOrder.max()
        return PortfolioTable.select(max).firstOrNull()?.get(max) ?: 0
    }

    @Transactional(readOnly = true)
    fun findAll(): List<PortfolioEntity> = PortfolioEntity.all().toList()

    private fun predicate(category: PortfolioCategory?, keyword: String?, visibleOnly: Boolean): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        category?.let { conditions += PortfolioTable.category eq it }
        keyword?.let { conditions += PortfolioTable.title like "%${ContentQueries.escapeLike(it)}%" }
        if (visibleOnly) conditions += PortfolioTable.visible eq true
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }
}

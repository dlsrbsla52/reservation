package com.media.bus.content.repository

import com.media.bus.content.entity.FaqEntity
import com.media.bus.content.entity.FaqTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class FaqRepository {

    @Transactional(readOnly = true)
    fun findAll(visibleOnly: Boolean): List<FaqEntity> {
        val query = if (visibleOnly) FaqEntity.find { FaqTable.visible eq true } else FaqEntity.all()
        return query.orderBy(FaqTable.sortOrder to SortOrder.ASC, FaqTable.createdAt to SortOrder.ASC).toList()
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): FaqEntity? = FaqEntity.findById(id)

    /** 정렬 순서 최댓값. 비어 있으면 0. */
    @Transactional(readOnly = true)
    fun maxSortOrder(): Int {
        val max = FaqTable.sortOrder.max()
        return FaqTable.select(max).firstOrNull()?.get(max) ?: 0
    }
}

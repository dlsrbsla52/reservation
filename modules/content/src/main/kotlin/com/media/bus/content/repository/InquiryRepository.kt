package com.media.bus.content.repository

import com.media.bus.content.dto.InquiryCreateRequest
import com.media.bus.content.entity.InquiryEntity
import com.media.bus.content.entity.InquiryTable
import com.media.bus.content.entity.enums.InquiryStatus
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Repository
class InquiryRepository {

    @Transactional(readOnly = true)
    fun findPage(status: InquiryStatus?, page: Int, size: Int): List<InquiryEntity> =
        InquiryTable.selectAll()
            .where { predicate(status) }
            .orderBy(InquiryTable.createdAt to SortOrder.DESC, InquiryTable.id to SortOrder.DESC)
            .limit(size)
            .offset(page.toLong() * size)
            .map { InquiryEntity.wrapRow(it) }

    @Transactional(readOnly = true)
    fun count(status: InquiryStatus?): Long = InquiryTable.selectAll().where { predicate(status) }.count()

    @Transactional(readOnly = true)
    fun findById(id: UUID): InquiryEntity? = InquiryEntity.findById(id)

    @Transactional
    fun create(request: InquiryCreateRequest, clientIp: String?) {
        InquiryEntity.create(request, clientIp)
    }

    /** 처리 완료 후 기준 시각이 지난 문의를 한 번의 DELETE로 지우고 건수를 돌려준다. */
    @Transactional
    fun deleteHandledBefore(cutoff: OffsetDateTime): Int =
        InquiryTable.deleteWhere { (InquiryTable.status eq InquiryStatus.DONE) and (InquiryTable.handledAt less cutoff) }

    private fun predicate(status: InquiryStatus?): Op<Boolean> = status?.let { InquiryTable.status eq it } ?: Op.TRUE
}

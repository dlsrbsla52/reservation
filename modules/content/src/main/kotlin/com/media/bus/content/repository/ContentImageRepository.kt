package com.media.bus.content.repository

import com.media.bus.content.entity.NoticeImageTable
import com.media.bus.content.entity.PortfolioTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class ContentImageRepository {

    /** 지정한 행을 뺀 다른 포트폴리오·공지 행이 이 키 중 하나라도 쓰는지 확인한다. */
    @Transactional(readOnly = true)
    fun isInUse(keys: Collection<String>, exceptPortfolioId: UUID? = null, exceptNoticeId: UUID? = null): Boolean {
        if (keys.isEmpty()) return false
        val portfolio = PortfolioTable.selectAll()
            .where {
                ((PortfolioTable.imageKey inList keys) or (PortfolioTable.thumbnailKey inList keys)) and
                    (exceptPortfolioId?.let { PortfolioTable.id neq it } ?: Op.TRUE)
            }
            .limit(1)
            .any()
        if (portfolio) return true
        return NoticeImageTable.selectAll()
            .where {
                (NoticeImageTable.imageKey inList keys) and
                    (exceptNoticeId?.let { NoticeImageTable.noticeId neq it } ?: Op.TRUE)
            }
            .limit(1)
            .any()
    }
}

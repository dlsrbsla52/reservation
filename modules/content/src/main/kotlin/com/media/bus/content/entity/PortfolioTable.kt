package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.content.entity.enums.PortfolioCategory

/** 포트폴리오 테이블 정의 */
object PortfolioTable : DateBaseTable("content.portfolio") {
    val title = varchar("title", 100)
    val category = enumerationByName<PortfolioCategory>("category", 20)
    val description = varchar("description", 500).nullable()
    val imageKey = varchar("image_key", 200)
    val thumbnailKey = varchar("thumbnail_key", 200)
    val sortOrder = integer("sort_order")
    val visible = bool("visible").default(true)
    val legacyIdx = integer("legacy_idx").nullable().uniqueIndex("uk_portfolio_legacy_idx")
}

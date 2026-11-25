package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 공지사항 테이블 정의 */
object NoticeTable : DateBaseTable("content.notice") {
    val title = varchar("title", 200)
    val body = text("body")
    val pinned = bool("pinned").default(false)
    val visible = bool("visible").default(true)
    val viewCount = integer("view_count").default(0)
    val publishedAt = timestampWithTimeZone("published_at")
    val legacyIdx = integer("legacy_idx").nullable().uniqueIndex("uk_notice_legacy_idx")
}

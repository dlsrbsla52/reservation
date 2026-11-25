package com.media.bus.content.entity

import com.media.bus.common.entity.common.BaseTable
import org.jetbrains.exposed.v1.core.ReferenceOption

/** 공지사항 첨부 이미지 테이블 정의 */
object NoticeImageTable : BaseTable("content.notice_image") {
    val noticeId = reference("notice_id", NoticeTable, onDelete = ReferenceOption.CASCADE, fkName = "fk_notice_image_notice")
        .index("idx_notice_image_notice_id")
    val imageKey = varchar("image_key", 200)
    val sortOrder = integer("sort_order")
}

package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.content.entity.enums.FaqCategory

/** FAQ 테이블 정의 */
object FaqTable : DateBaseTable("content.faq") {
    val category = enumerationByName<FaqCategory>("category", 20)
    val question = varchar("question", 300)
    val answer = text("answer")
    val sortOrder = integer("sort_order")
    val visible = bool("visible").default(true)
}

package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.content.dto.FaqCommand
import com.media.bus.content.entity.enums.FaqCategory
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class FaqEntity(id: EntityID<UUID>) : DateBaseEntity(id, FaqTable) {

    companion object : UUIDEntityClass<FaqEntity>(FaqTable) {

        /** FAQ를 목록 맨 뒤에 등록한다. */
        fun create(command: FaqCommand, category: FaqCategory, sortOrder: Int): FaqEntity =
            new(UuidV7.generate()) {
                this.category = category
                this.question = command.question.trim()
                this.answer = command.answer
                this.sortOrder = sortOrder
                this.visible = command.visible
            }
    }

    var category by FaqTable.category
    var question by FaqTable.question
    var answer by FaqTable.answer
    var sortOrder by FaqTable.sortOrder
    var visible by FaqTable.visible

    /** 질문·답변·분류·노출 여부를 바꾼다. */
    fun update(command: FaqCommand, category: FaqCategory) {
        this.category = category
        question = command.question.trim()
        answer = command.answer
        visible = command.visible
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
    }
}

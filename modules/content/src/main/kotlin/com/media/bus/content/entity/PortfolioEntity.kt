package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.entity.enums.PortfolioCategory
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class PortfolioEntity(id: EntityID<UUID>) : DateBaseEntity(id, PortfolioTable) {

    companion object : UUIDEntityClass<PortfolioEntity>(PortfolioTable) {

        /** 포트폴리오를 등록한다. 정렬 순서는 호출자가 목록 맨 앞 값을 계산해 넘긴다. */
        fun create(command: PortfolioCommand, category: PortfolioCategory, sortOrder: Int): PortfolioEntity =
            new(UuidV7.generate()) {
                this.title = command.title.trim()
                this.category = category
                this.description = command.description?.trim()?.takeIf { it.isNotEmpty() }
                this.imageKey = command.imageKey
                this.thumbnailKey = command.thumbnailKey
                this.sortOrder = sortOrder
                this.visible = command.visible
                this.legacyIdx = command.legacyIdx
            }
    }

    var title by PortfolioTable.title
    var category by PortfolioTable.category
    var description by PortfolioTable.description
    var imageKey by PortfolioTable.imageKey
    var thumbnailKey by PortfolioTable.thumbnailKey
    var sortOrder by PortfolioTable.sortOrder
    var visible by PortfolioTable.visible
    var legacyIdx by PortfolioTable.legacyIdx

    /** 내용을 바꾸고 교체되어 더 이상 쓰지 않는 이미지 키를 돌려준다. */
    fun update(command: PortfolioCommand, category: PortfolioCategory): List<String> {
        val replaced = (setOf(imageKey, thumbnailKey) - setOf(command.imageKey, command.thumbnailKey)).toList()
        title = command.title.trim()
        this.category = category
        description = command.description?.trim()?.takeIf { it.isNotEmpty() }
        imageKey = command.imageKey
        thumbnailKey = command.thumbnailKey
        visible = command.visible
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        return replaced
    }
}

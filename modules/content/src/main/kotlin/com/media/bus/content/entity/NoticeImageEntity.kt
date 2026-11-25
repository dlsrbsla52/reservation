package com.media.bus.content.entity

import com.media.bus.common.entity.common.BaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

class NoticeImageEntity(id: EntityID<UUID>) : BaseEntity(id) {

    companion object : UUIDEntityClass<NoticeImageEntity>(NoticeImageTable) {

        /** 공지사항 이미지를 순서와 함께 연결한다. */
        fun create(noticeId: UUID, imageKey: String, sortOrder: Int): NoticeImageEntity =
            new(UuidV7.generate()) {
                this.noticeId = EntityID(noticeId, NoticeTable)
                this.imageKey = imageKey
                this.sortOrder = sortOrder
            }
    }

    var noticeId by NoticeImageTable.noticeId
    var imageKey by NoticeImageTable.imageKey
    var sortOrder by NoticeImageTable.sortOrder
}

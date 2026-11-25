package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.content.dto.NoticeCommand
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class NoticeEntity(id: EntityID<UUID>) : DateBaseEntity(id, NoticeTable) {

    companion object : UUIDEntityClass<NoticeEntity>(NoticeTable) {

        /** 공지사항을 등록한다. 게시일이 없으면 지금 시각으로 둔다. */
        fun create(command: NoticeCommand): NoticeEntity =
            new(UuidV7.generate()) {
                this.title = command.title.trim()
                this.body = command.body
                this.pinned = command.pinned
                this.visible = command.visible
                this.viewCount = command.legacyViewCount ?: 0
                this.publishedAt = command.publishedAt ?: OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                this.legacyIdx = command.legacyIdx
            }
    }

    var title by NoticeTable.title
    var body by NoticeTable.body
    var pinned by NoticeTable.pinned
    var visible by NoticeTable.visible
    var viewCount by NoticeTable.viewCount
    var publishedAt by NoticeTable.publishedAt
    var legacyIdx by NoticeTable.legacyIdx

    /** 제목·본문·노출 설정을 바꾼다. 조회수와 이관 식별자는 유지한다. */
    fun update(command: NoticeCommand) {
        title = command.title.trim()
        body = command.body
        pinned = command.pinned
        visible = command.visible
        command.publishedAt?.let { publishedAt = it }
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
    }
}

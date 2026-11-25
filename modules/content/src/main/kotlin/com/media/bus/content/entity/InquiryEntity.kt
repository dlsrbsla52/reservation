package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.content.dto.InquiryCreateRequest
import com.media.bus.content.entity.enums.InquiryStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class InquiryEntity(id: EntityID<UUID>) : DateBaseEntity(id, InquiryTable) {

    companion object : UUIDEntityClass<InquiryEntity>(InquiryTable) {

        /** 공개 문의를 접수 상태로 저장한다. 개인정보 동의 여부는 호출 전에 검증한다. */
        fun create(request: InquiryCreateRequest, clientIp: String?): InquiryEntity =
            new(UuidV7.generate()) {
                this.name = request.name.trim()
                this.phone = request.phone.trim()
                this.email = request.email.trim()
                this.content = request.content
                this.privacyAgreed = true
                this.clientIp = clientIp
                this.status = InquiryStatus.RECEIVED
            }
    }

    var name by InquiryTable.name
    var phone by InquiryTable.phone
    var email by InquiryTable.email
    var content by InquiryTable.content
    var privacyAgreed by InquiryTable.privacyAgreed
    var clientIp by InquiryTable.clientIp
    var status by InquiryTable.status
    var adminMemo by InquiryTable.adminMemo
    var handledById by InquiryTable.handledById
    var handledAt by InquiryTable.handledAt

    /** 처리 상태를 바꾸고 처리자를 기록한다. 메모는 null이면 유지하고 빈 문자열이면 지운다. */
    fun changeStatus(status: InquiryStatus, memo: String?, adminId: UUID) {
        val now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        this.status = status
        if (memo != null) adminMemo = memo.trim().takeIf { it.isNotEmpty() }
        handledById = adminId
        handledAt = now
        updatedAt = now
    }
}

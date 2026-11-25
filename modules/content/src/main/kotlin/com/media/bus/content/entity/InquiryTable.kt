package com.media.bus.content.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.content.entity.enums.InquiryStatus
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 온라인 문의 테이블 정의 */
object InquiryTable : DateBaseTable("content.inquiry") {
    val name = varchar("name", 50)
    val phone = varchar("phone", 20)
    val email = varchar("email", 100)
    val content = text("content")
    val privacyAgreed = bool("privacy_agreed")
    val clientIp = varchar("client_ip", 45).nullable()
    val status = enumerationByName<InquiryStatus>("status", 20).index("idx_inquiry_status")
    val adminMemo = text("admin_memo").nullable()
    val handledById = javaUUID("handled_by_id").nullable()
    val handledAt = timestampWithTimeZone("handled_at").nullable()
}

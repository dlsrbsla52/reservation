package com.media.bus.content.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 온라인 문의 처리 상태 */
enum class InquiryStatus(
    override val displayName: String,
) : BaseEnum {
    RECEIVED("접수"),
    DONE("처리 완료"),
    ;

    companion object {
        fun fromName(name: String): InquiryStatus? = BaseEnum.fromName<InquiryStatus>(name)
    }
}

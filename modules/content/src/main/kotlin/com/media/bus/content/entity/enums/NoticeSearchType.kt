package com.media.bus.content.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 공지사항 검색 대상 */
enum class NoticeSearchType(
    override val displayName: String,
) : BaseEnum {
    TITLE("제목"),
    BODY("본문"),
    ;

    companion object {
        fun fromName(name: String): NoticeSearchType? = BaseEnum.fromName<NoticeSearchType>(name)
    }
}

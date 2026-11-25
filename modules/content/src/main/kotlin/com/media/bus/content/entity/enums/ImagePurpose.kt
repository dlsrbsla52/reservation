package com.media.bus.content.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 업로드 이미지 용도. 객체 키 접두사가 된다 */
enum class ImagePurpose(
    override val displayName: String,
) : BaseEnum {
    PORTFOLIO("포트폴리오"),
    NOTICE("공지사항"),
    ;

    companion object {
        fun fromName(name: String): ImagePurpose? = BaseEnum.fromName<ImagePurpose>(name)
    }
}

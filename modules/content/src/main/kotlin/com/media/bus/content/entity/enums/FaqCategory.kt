package com.media.bus.content.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** FAQ 분류 */
enum class FaqCategory(
    override val displayName: String,
) : BaseEnum {
    MEDIA("서울버스TV 매체"),
    HOSPITAL("병원 광고"),
    ;

    companion object {
        fun fromName(name: String): FaqCategory? = BaseEnum.fromName<FaqCategory>(name)
    }
}

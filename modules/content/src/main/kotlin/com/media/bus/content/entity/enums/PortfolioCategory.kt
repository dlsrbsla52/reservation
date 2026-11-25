package com.media.bus.content.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 포트폴리오 분류 */
enum class PortfolioCategory(
    override val displayName: String,
) : BaseEnum {
    HOSPITAL("병원"),
    ACADEMY("학원"),
    FNB("F&B"),
    ETC("기타"),
    ;

    companion object {
        fun fromName(name: String): PortfolioCategory? = BaseEnum.fromName<PortfolioCategory>(name)
    }
}

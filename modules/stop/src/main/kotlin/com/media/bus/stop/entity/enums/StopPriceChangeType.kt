package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum

@Suppress("unused")
enum class StopPriceChangeType(
    override val displayName: String,
) : BaseEnum {
    CREATE("등록"),
    UPDATE("수정"),
    DELETE("삭제"),
    ;

    companion object {
        fun fromName(name: String): StopPriceChangeType? = BaseEnum.fromName<StopPriceChangeType>(name)
    }
}

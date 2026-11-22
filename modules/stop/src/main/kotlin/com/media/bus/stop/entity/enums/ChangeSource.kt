package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 변경 주체 Enum */
@Suppress("unused")
enum class ChangeSource(
    override val displayName: String,
) : BaseEnum {
    USER("사용자"),
    SYSTEM("시스템"),
}

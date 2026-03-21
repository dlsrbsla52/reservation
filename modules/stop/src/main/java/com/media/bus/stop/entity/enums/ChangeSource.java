package com.media.bus.stop.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum ChangeSource implements BaseEnum {
    USER("USER", "사용자"),
    SYSTEM("SYSTEM", "시스템"),
    ;

    private final String name;
    private final String displayName;
}
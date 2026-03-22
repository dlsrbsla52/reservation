package com.media.bus.stop.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum StopType implements BaseEnum {
    VILLAGE_BUS("VILLAGE_BUS", "마을버스"),
    ROADSIDE_ALL_DAY("ROADSIDE_ALL_DAY", "가로변전일"),
    ROADSIDE_TIMED("ROADSIDE_TIMED", "가로변시간"),
    CENTER_LANE("CENTER_LANE", "중앙차로"),
    GENERAL_LANE("GENERAL_LANE", "일반차로"),
    HANGANG_DOCK("HANGANG_DOCK", "한강선착장"),
    ;

    private final String name;
    private final String displayName;

    // displayName → StopType 역방향 조회를 위한 O(1) 캐시 (공공 API 연동 시 빈번히 호출됨)
    private static final Map<String, StopType> BY_DISPLAY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(StopType::getDisplayName, Function.identity()));

    public static Optional<StopType> fromName(String name) {
        return BaseEnum.fromName(StopType.class, name);
    }

    public static StopType fromDisplayName(String displayName) {
        StopType type = BY_DISPLAY_NAME.get(displayName);
        if (type == null) throw new IllegalArgumentException("Unknown stop type: " + displayName);
        return type;
    }
}
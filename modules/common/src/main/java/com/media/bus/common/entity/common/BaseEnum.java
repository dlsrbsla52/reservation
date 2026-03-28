package com.media.bus.common.entity.common;

import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Optional;

/// 공통 Enum 처리를 위한 베이스 인터페이스.
/// 모든 코드성 Enum 객체는 해당 인터페이스를 구현하여 다형성 및 일관된 데이터 추출 방식을 보장합니다.
@SuppressWarnings("unused")
public interface BaseEnum {

    /// Enum의 고유 코드(이름) 반환
    String getName();

    /// Enum의 전시용 이름 반환
    String getDisplayName();

    /// Enum 클래스와 name 값을 기반으로 해당하는 Enum 상수를 조회하는 공통 메서드.
    ///
    /// @param enumClass 조회할 Enum의 Class 메타데이터
    /// @param name      검색할 Enum의 이름 (코드)
    /// @param <T>       BaseEnum을 구현한 Enum 타입
    /// @return 일치하는 Enum 상수가 존재하면 Optional로 래핑하여 반환
    @NullMarked
    static <T extends Enum<T> & BaseEnum> Optional<T> fromName(Class<T> enumClass, String name) {
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getName().equals(name))
                .findFirst();
    }
}

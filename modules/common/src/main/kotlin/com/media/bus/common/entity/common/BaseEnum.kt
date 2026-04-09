package com.media.bus.common.entity.common

/**
 * ## 공통 Enum 처리를 위한 베이스 인터페이스
 *
 * 모든 코드성 Enum 객체는 해당 인터페이스를 구현하여 다형성 및 일관된 데이터 추출 방식을 보장한다.
 */
interface BaseEnum {
    val name: String
    val displayName: String

    companion object {
        /**
         * Enum 클래스와 name 값을 기반으로 해당하는 Enum 상수를 조회하는 공통 메서드.
         *
         * @param name 검색할 Enum의 이름 (코드)
         * @return 일치하는 Enum 상수가 존재하면 해당 값, 없으면 null
         */
        inline fun <reified T> fromName(name: String): T? where T : Enum<T>, T : BaseEnum =
            enumValues<T>().find { it.name == name }
    }
}

package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum
import com.media.bus.common.exceptions.BusinessException
import org.springframework.http.HttpStatus

/**
 * ## 정류소 유형 Enum
 *
 * 서울 열린데이터광장 공공 API의 `STOPS_TYPE` 필드에 대응한다.
 * `displayName`은 공공 API 원본 한글 값이며 역방향 조회에 사용된다.
 */
@Suppress("unused")
enum class StopType(
    override val displayName: String,
) : BaseEnum {
    VILLAGE_BUS("마을버스"),
    ROADSIDE_ALL_DAY("가로변전일"),
    ROADSIDE_TIMED("가로변시간"),
    CENTER_LANE("중앙차로"),
    GENERAL_LANE("일반차로"),
    HANGANG_DOCK("한강선착장"),
    ;

    companion object {
        // displayName -> StopType 역방향 조회를 위한 O(1) 캐시 (공공 API 연동 시 빈번히 호출됨)
        private val byDisplayName = entries.associateBy { it.displayName }

        fun fromName(name: String): StopType? = BaseEnum.fromName<StopType>(name)

        fun fromDisplayName(displayName: String): StopType =
            byDisplayName[displayName] ?: error("알 수 없는 정류소 유형: $displayName")

        /**
         * 조회 필터 파라미터를 파싱한다. 값이 비어 있으면 `null`(= 필터 없음)이다.
         *
         * 컨트롤러에서 `StopType?` 로 직접 바인딩하지 않는 이유는 좌표를 `String` 으로 받는 것과 같다.
         * 스프링 바인딩 단계에서 실패하면 `ExceptionAdvisor` 를 타지 않아 도메인 메시지 대신
         * 프레임워크 기본 메시지가 응답된다.
         *
         * iam 은 stop 모듈에 의존하지 않아 이 enum 을 참조할 수 없고, S2S 경로로도 문자열이 넘어온다.
         * 따라서 문자열 → enum 변환 지점을 여기 한 곳으로 모은다.
         *
         * @throws BusinessException 알 수 없는 유형이면 400 BAD_REQUEST
         */
        fun parseFilter(raw: String?): StopType? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return fromName(value) ?: throw BusinessException(
                HttpStatus.BAD_REQUEST,
                "알 수 없는 정류소 유형입니다: $value (가능한 값: ${entries.joinToString(", ") { it.name }})",
            )
        }
    }
}

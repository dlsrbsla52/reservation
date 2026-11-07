package com.media.bus.contract.security

import com.media.bus.common.entity.common.BaseEnum
import io.jsonwebtoken.Claims
import java.time.Duration

/**
 * ## 토큰 발급처(audience)
 *
 * 같은 회원이라도 **어느 사이트에서 로그인했는지**를 토큰에 남겨, 사이트 간 토큰 재사용을 막는다.
 * - 일반 사이트에서 발급된 토큰(`USER`)은 gateway가 `/api/v1/admin` 하위 경로 진입을 거부한다.
 * - 관리자 사이트 토큰(`ADMIN`)은 탈취 시 피해가 크므로 액세스 토큰 TTL을 짧게 둔다.
 *
 * JWT 표준 `aud` 클레임에 enum `name`을 그대로 기록한다.
 *
 * @property accessTokenTtl 이 발급처로 발급하는 액세스 토큰의 유효 시간
 */
enum class TokenAudience(
    override val displayName: String,
    val accessTokenTtl: Duration,
) : BaseEnum {
    USER("일반 사이트", Duration.ofMinutes(60)),
    ADMIN("관리자 사이트", Duration.ofMinutes(15)),
    ;

    companion object {
        fun fromName(name: String): TokenAudience? = BaseEnum.fromName<TokenAudience>(name)

        /**
         * 클레임에서 발급처를 읽는다.
         *
         * `aud`가 없는 토큰(이 기능 도입 전에 발급된 토큰)은 **`USER`로 간주**한다.
         * 권한이 넓은 쪽(`ADMIN`)으로 해석하면 구버전 토큰이 관리자 경계를 우회하게 되므로,
         * 모르는 값도 같은 이유로 `USER`로 떨어뜨린다.
         */
        fun fromClaims(claims: Claims): TokenAudience =
            claims.audience?.firstNotNullOfOrNull(::fromName) ?: USER
    }
}

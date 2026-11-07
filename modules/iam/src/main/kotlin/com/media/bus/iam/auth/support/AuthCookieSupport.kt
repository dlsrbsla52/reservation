package com.media.bus.iam.auth.support

import com.media.bus.contract.security.TokenAudience
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * ## 인증 컨트롤러 공용 Refresh Token 쿠키·요청 정보 헬퍼
 *
 * 발급처([TokenAudience])마다 **쿠키 이름과 Path를 분리**한다.
 * - 일반 사이트: `refresh_token`, Path `/api/v1/auth`
 * - 관리자 사이트: `admin_refresh_token`, Path `/api/v1/auth/admin`
 *
 * 쿠키를 하나로 두면 같은 브라우저에서 관리자 로그인 후 일반 화면의 스크립트가
 * 일반 갱신 API로 관리자 세션을 갱신해 관리자 토큰을 얻을 수 있다.
 * 관리자 쿠키는 `/api/v1/auth/admin` 하위로만 전송되고, 그 경로는 gateway CORS가 관리자 origin만 허용한다.
 */
@Component
class AuthCookieSupport(
    /** `cookie.secure` 설정값. 운영 환경(HTTPS)에서는 true, 로컬 개발(HTTP)에서는 false. */
    // 기본값은 @Value 에만 둔다(Kotlin 기본값을 함께 두면 생성자가 둘이 되어 주입 대상이 모호해진다).
    @param:Value($$"${cookie.secure:false}") private val cookieSecure: Boolean,
) {
    companion object {
        const val USER_REFRESH_COOKIE = "refresh_token"
        const val ADMIN_REFRESH_COOKIE = "admin_refresh_token"

        /** Refresh Token TTL — JwtProvider의 Refresh Token TTL과 반드시 동일해야 한다. */
        private val REFRESH_TOKEN_TTL = Duration.ofDays(7)

        private fun cookieName(audience: TokenAudience) = when (audience) {
            TokenAudience.USER -> USER_REFRESH_COOKIE
            TokenAudience.ADMIN -> ADMIN_REFRESH_COOKIE
        }

        private fun cookiePath(audience: TokenAudience) = when (audience) {
            TokenAudience.USER -> "/api/v1/auth"
            TokenAudience.ADMIN -> "/api/v1/auth/admin"
        }
    }

    /** Refresh Token을 발급처에 맞는 HttpOnly Cookie로 설정한다. */
    fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String, audience: TokenAudience) =
        addCookie(response, audience, refreshToken, REFRESH_TOKEN_TTL)

    /** 발급처에 맞는 Refresh Token Cookie를 즉시 만료시킨다 (로그아웃 시 사용). */
    fun expireRefreshTokenCookie(response: HttpServletResponse, audience: TokenAudience) =
        addCookie(response, audience, "", Duration.ZERO)

    private fun addCookie(response: HttpServletResponse, audience: TokenAudience, value: String, maxAge: Duration) {
        val cookie = ResponseCookie.from(cookieName(audience), value)
            .httpOnly(true)
            .secure(cookieSecure)
            .path(cookiePath(audience))
            .maxAge(maxAge)
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    /** User-Agent를 기기 정보로 추출한다 (최대 500자). */
    fun extractDeviceInfo(request: HttpServletRequest): String? =
        request.getHeader("User-Agent")?.take(500)

    /**
     * 클라이언트 IP를 추출한다.
     * X-Forwarded-For 헤더(첫 번째 값) 우선 — Gateway/Load Balancer 뒤에서 실제 클라이언트 IP 보존.
     */
    fun extractClientIp(request: HttpServletRequest): String? {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.substringBefore(",").trim().ifBlank { null }
        }
        return request.remoteAddr
    }
}

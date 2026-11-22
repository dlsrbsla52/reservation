package com.media.bus.iam.auth.support

import com.media.bus.contract.security.TokenAudience
import com.media.bus.iam.support.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/** 인증 컨트롤러 공용 Refresh Token 쿠키·요청 정보 헬퍼 */
@Component
class AuthCookieSupport(
    @param:Value($$"${cookie.secure:false}") private val cookieSecure: Boolean,
) {
    companion object {
        const val USER_REFRESH_COOKIE = "refresh_token"
        const val ADMIN_REFRESH_COOKIE = "admin_refresh_token"

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

    /** 클라이언트 IP를 추출한다. */
    fun extractClientIp(request: HttpServletRequest): String? = ClientIpResolver.resolve(request)
}

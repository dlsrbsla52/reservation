package com.media.bus.app.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * ## CORS 설정 (예전 gateway `CorsConfig`의 서블릿판)
 *
 * Spring Security 체인의 `CorsFilter`가 이 설정으로 preflight(OPTIONS)를 인증 필터보다 먼저 처리한다.
 *
 * **경로별 origin 분리**
 * - 관리자 API(`/api/v1/admin`)와 관리자 인증(`/api/v1/auth/admin`)은 관리자 origin만 허용한다.
 *   일반 사이트 스크립트가 관리자 refresh 쿠키로 갱신 API를 호출해도 응답(토큰)을 읽지 못하게 하기 위함이다.
 * - 그 외 경로는 일반·관리자 origin 모두 허용한다. 관리자 화면도 `/api/v1/member/me` 등 공통 API를 쓴다.
 */
@Configuration
class CorsConfig(
    /** 일반 사이트 origin(`CORS_ALLOWED_ORIGIN`). 로컬 기본값은 application-local.yml 의 http://localhost:3000 */
    @param:Value("\${cors.allowed-origin}")
    private val allowedOrigin: String,
    /**
     * 관리자 사이트 origin. 비어 있으면 일반 origin과 같다고 본다(로컬 단일 Next 앱).
     * Kotlin 기본값을 두지 않는다 — 생성자가 둘이 되면 @Configuration CGLIB 프록시가 생성자를 고르지 못해 기동이 실패한다.
     */
    @param:Value("\${cors.admin-allowed-origin:}")
    private val adminAllowedOrigin: String,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val adminOrigin = adminAllowedOrigin.ifBlank { allowedOrigin }
        val adminOnly = corsConfiguration(listOf(adminOrigin))
        val common = corsConfiguration(listOf(allowedOrigin, adminOrigin).distinct())

        return UrlBasedCorsConfigurationSource().apply {
            // 관리자 전용 패턴을 "/**" 보다 먼저 등록한다(먼저 매칭된 설정 사용)
            ADMIN_ONLY_PATTERNS.forEach { registerCorsConfiguration(it, adminOnly) }
            registerCorsConfiguration("/**", common)
        }
    }

    private fun corsConfiguration(origins: List<String>) = CorsConfiguration().apply {
        // withCredentials: true 사용 시 allowedOrigins에 * 와일드카드 불가 — 명시적 origin 필수
        allowedOrigins = origins
        allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        allowedHeaders = listOf("*")
        allowCredentials = true
        maxAge = 3600L
    }

    companion object {
        /** 관리자 origin만 허용하는 경로. */
        val ADMIN_ONLY_PATTERNS = listOf("/api/v1/admin/**", "/api/v1/auth/admin/**")
    }
}

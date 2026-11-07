package com.media.bus.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/// Gateway CORS 설정.
/// Spring Security 필터 체인이 Gateway 라우팅 필터보다 먼저 실행되므로,
/// Spring Security에도 동일한 CORS 설정을 명시적으로 등록해야 preflight(OPTIONS) 요청이 403으로 차단되지 않습니다.
/// SecurityConfig에서 `.cors(Customizer.withDefaults())`를 통해 이 Bean을 참조합니다.
///
/// **경로별 origin 분리**
/// - 관리자 API(`/api/v1/admin`)와 관리자 인증(`/api/v1/auth/admin`)은 관리자 origin 만 허용합니다.
///   일반 사이트 스크립트가 관리자 refresh 쿠키로 갱신 API를 호출해도 응답(토큰)을 읽지 못하게 하기 위함입니다.
/// - 그 외 경로는 일반·관리자 origin 모두 허용합니다. 관리자 화면도 `/api/v1/member/me` 등 공통 API를 씁니다.
@Configuration
class CorsConfig(
    /// 일반 사이트 origin. 운영 환경에서는 CORS_ALLOWED_ORIGIN 환경변수로 주입합니다.
    /// 로컬 개발 환경은 application-local.yml에서 http://localhost:3000 으로 설정됩니다.
    @Value("\${CORS_ALLOWED_ORIGIN}")
    private val allowedOrigin: String,
    /// 관리자 사이트 origin. 비어 있으면 일반 origin 과 같다고 보고(로컬 단일 Next 앱) 그대로 사용합니다.
    /// Kotlin 기본값을 두지 않는다 — 생성자가 둘이 되면 @Configuration CGLIB 프록시가 생성자를 고르지 못해 기동이 실패한다.
    @Value("\${CORS_ADMIN_ALLOWED_ORIGIN:}")
    private val adminAllowedOrigin: String,
) {

    companion object {
        /// 관리자 origin 만 허용하는 경로. 반드시 "/**" 보다 먼저 등록해야 합니다(먼저 매칭된 설정 사용).
        val ADMIN_ONLY_PATTERNS = listOf("/api/v1/admin/**", "/api/v1/auth/admin/**")
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val adminOrigin = adminAllowedOrigin.ifBlank { allowedOrigin }
        val adminOnly = corsConfiguration(listOf(adminOrigin))
        val common = corsConfiguration(listOf(allowedOrigin, adminOrigin).distinct())

        return UrlBasedCorsConfigurationSource().apply {
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
}

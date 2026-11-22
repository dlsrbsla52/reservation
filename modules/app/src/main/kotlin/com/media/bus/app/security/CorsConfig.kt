package com.media.bus.app.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/** CORS 설정 (예전 gateway `CorsConfig`의 서블릿판) */
@Configuration
class CorsConfig(
    @param:Value("\${cors.allowed-origin}")
    private val allowedOrigin: String,
    @param:Value("\${cors.admin-allowed-origin:}")
    private val adminAllowedOrigin: String,
) {

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
        allowedOrigins = origins
        allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        allowedHeaders = listOf("*")
        allowCredentials = true
        maxAge = 3600L
    }

    companion object {
        val ADMIN_ONLY_PATTERNS = listOf("/api/v1/admin/**", "/api/v1/auth/admin/**")
    }
}

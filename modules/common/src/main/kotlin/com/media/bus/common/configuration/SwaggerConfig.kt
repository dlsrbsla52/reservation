package com.media.bus.common.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ## Swagger(OpenAPI) 설정
 */
@Configuration
@ConditionalOnClass(OpenAPI::class)
class SwaggerConfig {

    companion object {
        private const val SECURITY_SCHEME_NAME = "BearerAuth"
    }

    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("ACT Boilerplate API")
                    .description("API 명세서")
                    .version("v1.0.0")
            )
            // JWT 인증 설정 (헤더에 Bearer Token 추가 기능)
            .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .components(
                Components()
                    .addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        SecurityScheme()
                            .name(SECURITY_SCHEME_NAME)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
}

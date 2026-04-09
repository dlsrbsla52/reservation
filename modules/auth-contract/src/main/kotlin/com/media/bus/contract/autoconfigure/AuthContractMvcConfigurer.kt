package com.media.bus.contract.autoconfigure

import com.media.bus.contract.security.interceptor.AuthorizeHandlerInterceptor
import com.media.bus.contract.security.resolver.CurrentMemberArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * ## auth-contract 모듈의 MVC 구성 등록
 *
 * AuthContractAutoConfiguration에 의해 Servlet 환경에서만 빈으로 등록됩니다.
 * - AuthorizeHandlerInterceptor: /api 하위 전체 경로에서 @Authorize 어노테이션 기반 인가 처리
 * - CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입 처리
 */
class AuthContractMvcConfigurer(
    private val authorizeHandlerInterceptor: AuthorizeHandlerInterceptor,
    private val currentMemberArgumentResolver: CurrentMemberArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authorizeHandlerInterceptor)
            .addPathPatterns("/api/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentMemberArgumentResolver)
    }
}

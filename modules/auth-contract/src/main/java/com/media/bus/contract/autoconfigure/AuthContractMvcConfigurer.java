package com.media.bus.contract.autoconfigure;

import com.media.bus.contract.security.interceptor.AuthorizeHandlerInterceptor;
import com.media.bus.contract.security.resolver.CurrentMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/// auth-contract 모듈의 MVC 구성 등록.
/// AuthContractAutoConfiguration에 의해 Servlet 환경에서만 빈으로 등록됩니다.
/// - AuthorizeHandlerInterceptor: /api/\*\* 경로에서 @Authorize 어노테이션 기반 인가 처리
/// - CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입 처리
@RequiredArgsConstructor
public class AuthContractMvcConfigurer implements WebMvcConfigurer {

    private final AuthorizeHandlerInterceptor authorizeHandlerInterceptor;
    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizeHandlerInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}

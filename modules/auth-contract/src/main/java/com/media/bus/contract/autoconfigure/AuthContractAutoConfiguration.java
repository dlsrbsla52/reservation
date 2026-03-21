package com.media.bus.contract.autoconfigure;

import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.filter.MemberPrincipalExtractFilter;
import com.media.bus.contract.security.interceptor.AuthorizeHandlerInterceptor;
import com.media.bus.contract.security.resolver.CurrentMemberArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * auth-contract 모듈 자동 구성.
 *
 * 항상 등록: JwtProvider (토큰 생성·파싱·검증)
 *
 * Servlet 환경 전용:
 * - MemberPrincipalExtractFilter: X-User-* 헤더 → MemberPrincipal 복원
 * - AuthorizeHandlerInterceptor:  @Authorize 어노테이션 인가 처리
 * - CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입
 * - AuthContractMvcConfigurer:     위 인터셉터/리졸버를 MVC에 등록
 *
 * Gateway 모듈은 WebFlux(Reactive)이므로 Servlet 조건에 해당하지 않아
 * MVC 빈들은 등록되지 않습니다.
 */
@AutoConfiguration
public class AuthContractAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(
        @Value("${jwt.secret}") String secret,
        StringRedisTemplate redisTemplate
    ) {
        return new JwtProvider(secret, redisTemplate);
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebMvcBeans {

        @Bean
        public MemberPrincipalExtractFilter memberPrincipalExtractFilter() {
            return new MemberPrincipalExtractFilter();
        }

        @Bean
        public AuthorizeHandlerInterceptor authorizeHandlerInterceptor() {
            return new AuthorizeHandlerInterceptor();
        }

        @Bean
        public CurrentMemberArgumentResolver currentMemberArgumentResolver() {
            return new CurrentMemberArgumentResolver();
        }

        @Bean
        public AuthContractMvcConfigurer authContractMvcConfigurer(
            AuthorizeHandlerInterceptor interceptor,
            CurrentMemberArgumentResolver resolver
        ) {
            return new AuthContractMvcConfigurer(interceptor, resolver);
        }
    }
}

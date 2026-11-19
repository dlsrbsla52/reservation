package com.media.bus.contract.autoconfigure

import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.contract.security.interceptor.AuthorizeHandlerInterceptor
import com.media.bus.contract.security.resolver.CurrentMemberArgumentResolver
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * ## auth-contract 모듈 자동 구성
 *
 * 항상 등록: JwtProvider (토큰 생성/파싱/검증)
 *
 * Servlet 환경 전용:
 * - AuthorizeHandlerInterceptor: @Authorize 어노테이션 인가 처리
 * - CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입
 * - AuthContractMvcConfigurer: 위 인터셉터/리졸버를 MVC에 등록
 *
 * `MemberPrincipal`은 `app` 모듈의 JWT 인증 필터가 request attribute로 넣는다.
 * 예전의 `MemberPrincipalExtractFilter`(X-User-* 헤더 신뢰)는 헤더 위조로 다른 회원 행세가 가능해 제거했다.
 */
@AutoConfiguration
class AuthContractAutoConfiguration {

    @Bean
    fun jwtProvider(
        @Value("\${jwt.secret}") secret: String,
        @Value("\${jwt.claim-encryption-key}") claimEncryptionKey: String,
        redisTemplate: StringRedisTemplate,
    ): JwtProvider = JwtProvider(secret, claimEncryptionKey, redisTemplate)

    @Configuration
    @ConditionalOnClass(SpringDocUtils::class)
    class SpringDocBeans {
        init {
            // @CurrentMember 파라미터는 ArgumentResolver가 처리하므로 Swagger 문서에서 숨김
            SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentMember::class.java)
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    class WebMvcBeans {

        @Bean
        fun authorizeHandlerInterceptor(): AuthorizeHandlerInterceptor =
            AuthorizeHandlerInterceptor()

        @Bean
        fun currentMemberArgumentResolver(): CurrentMemberArgumentResolver =
            CurrentMemberArgumentResolver()

        @Bean
        fun authContractMvcConfigurer(
            interceptor: AuthorizeHandlerInterceptor,
            resolver: CurrentMemberArgumentResolver,
        ): AuthContractMvcConfigurer = AuthContractMvcConfigurer(interceptor, resolver)
    }
}

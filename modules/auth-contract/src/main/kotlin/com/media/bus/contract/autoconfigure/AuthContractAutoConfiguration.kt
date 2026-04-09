package com.media.bus.contract.autoconfigure

import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.contract.security.filter.MemberPrincipalExtractFilter
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
 * - MemberPrincipalExtractFilter: X-User-* 헤더 -> MemberPrincipal 복원
 * - AuthorizeHandlerInterceptor: @Authorize 어노테이션 인가 처리
 * - CurrentMemberArgumentResolver: @CurrentMember 파라미터 주입
 * - AuthContractMvcConfigurer: 위 인터셉터/리졸버를 MVC에 등록
 *
 * Gateway 모듈은 WebFlux(Reactive)이므로 Servlet 조건에 해당하지 않아
 * MVC 빈들은 등록되지 않습니다.
 */
@AutoConfiguration
class AuthContractAutoConfiguration {

    @Bean
    fun jwtProvider(
        @Value("\${jwt.secret}") secret: String,
        redisTemplate: StringRedisTemplate,
    ): JwtProvider = JwtProvider(secret, redisTemplate)

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
        fun memberPrincipalExtractFilter(): MemberPrincipalExtractFilter =
            MemberPrincipalExtractFilter()

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

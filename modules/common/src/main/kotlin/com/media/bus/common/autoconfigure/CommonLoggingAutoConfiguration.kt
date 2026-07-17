package com.media.bus.common.autoconfigure

import com.media.bus.common.logging.AccessLoggingFilter
import com.media.bus.common.logging.MdcLoggingFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CommonLoggingAutoConfiguration {

    /** Gateway가 전달한 요청 헤더를 MDC에 주입하는 필터를 등록한다. */
    @Bean
    fun mdcLoggingFilter(): FilterRegistrationBean<MdcLoggingFilter> =
        FilterRegistrationBean(MdcLoggingFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + 200
            addUrlPatterns("/*")
        }

    /**
     * 액세스 로깅 필터를 MDC 필터 다음 순서로 등록한다.
     * `+300` 으로 `MdcLoggingFilter(+200)` 보다 늦게 시작 → MDC 컨텍스트 안에서 실행되어
     * `requestId` / `memberId` 가 액세스 로그에도 함께 출력된다.
     */
    @Bean
    fun accessLoggingFilter(): FilterRegistrationBean<AccessLoggingFilter> =
        FilterRegistrationBean(AccessLoggingFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + 300
            addUrlPatterns("/*")
        }
}

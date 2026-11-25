package com.media.bus.app.web

import jakarta.servlet.Filter
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import java.util.concurrent.atomic.AtomicReference

/** 서블릿 필터에 도달한 요청의 원격 주소를 기록하는 테스트 설정 */
@TestConfiguration
class RemoteAddressTestConfig {

    @Bean
    fun remoteAddressRecorder(): AtomicReference<String> = AtomicReference()

    @Bean
    fun remoteAddressFilter(recorder: AtomicReference<String>): FilterRegistrationBean<Filter> =
        FilterRegistrationBean<Filter>(
            Filter { request, response, chain ->
                recorder.set(request.remoteAddr)
                chain.doFilter(request, response)
            },
        ).apply { order = Ordered.HIGHEST_PRECEDENCE }
}

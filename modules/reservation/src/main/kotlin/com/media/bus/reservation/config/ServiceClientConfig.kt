package com.media.bus.reservation.config

import com.media.bus.common.client.S2SRestClientFactory
import com.media.bus.reservation.contract.client.IamApi
import com.media.bus.reservation.reservation.client.StopApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * reservation 모듈의 외부 서비스 클라이언트 프록시 Bean 등록.
 *
 * 설계 의도:
 * - `S2SRestClientFactory.createProxy()`를 통해 `@HttpExchange` 프록시를 생성한다.
 * - 각 프록시는 S2S 토큰 인터셉터가 적용된 RestClient를 기반으로 동작한다.
 * - 서비스 URL은 `application.yml`의 설정값으로 관리한다.
 */
@Configuration
class ServiceClientConfig {

    @Bean
    fun stopApi(
        @Value("\${services.stop.url}") stopServiceUrl: String,
        s2sRestClientFactory: S2SRestClientFactory,
    ): StopApi = s2sRestClientFactory.createProxy(stopServiceUrl, StopApi::class.java)

    @Bean
    fun iamApi(
        @Value("\${services.iam.url}") iamServiceUrl: String,
        s2sRestClientFactory: S2SRestClientFactory,
    ): IamApi = s2sRestClientFactory.createProxy(iamServiceUrl, IamApi::class.java)
}

package com.media.bus.common.configuration

import com.media.bus.common.security.TokenProvider
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Spring Boot 4에서 `@RestClientTest`가 제거됨에 따라 MockK 기반 순수 단위 테스트로 전환.
 * `RestClient.Builder`에 `MockRestServiceServer`를 직접 바인딩하여
 * HTTP 인터셉터(토큰 전파) 동작을 Spring 컨텍스트 없이 검증한다.
 */
class RestClientConfigTest {

    /**
     * `ObjectProvider<HttpServletRequest>`의 테스트 전용 구현체.
     * 스레드 로컬로 요청을 저장하여 테스트가 독립적으로 컨텍스트를 제어할 수 있다.
     */
    class MockRequestHolder : ObjectProvider<HttpServletRequest> {
        private val local = ThreadLocal<HttpServletRequest>()

        fun set(req: HttpServletRequest) = local.set(req)
        fun clear() = local.remove()

        override fun getIfAvailable(): HttpServletRequest? = local.get()

        override fun getObject(): HttpServletRequest =
            local.get() ?: throw IllegalStateException("요청 컨텍스트 없음")
    }

    private val requestHolder = MockRequestHolder()
    private val tokenProvider = mockk<TokenProvider>()

    private lateinit var internalRestClient: RestClient
    private lateinit var mockServer: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        // MockRestServiceServer.bindTo(builder)로 목 서버를 바인딩한 뒤
        // 같은 builder를 RestClientConfig.buildWith()에 전달하여 인터셉터까지 적용된 RestClient를 조립한다.
        val builder = RestClient.builder()
        mockServer = MockRestServiceServer.bindTo(builder).build()
        internalRestClient = RestClientConfig(tokenProvider, requestHolder).buildWith(builder)
        requestHolder.clear()
    }

    @AfterEach
    fun tearDown() {
        requestHolder.clear()
    }

    @Test
    @DisplayName("1. 유저 요청(Request Context)이 존재할 때: HttpHeaders.AUTHORIZATION 이 정상적으로 전파되는지 검증")
    fun shouldPropagateUserTokenWhenRequestContextExists() {
        // Given: 가상의 외부 유저 요청(HttpServletRequest) 환경 세팅
        val validUserToken = "VALID_USER_JWT_TOKEN"
        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $validUserToken")
        requestHolder.set(request)

        val targetUri = "http://internal-service/api/v1/resource"

        // Mock Server: 해당 타겟 URL로 정상적인 User Token(Bearer) 헤더가 포함된 GET 요청이 올 것을 기대함
        mockServer.expect(requestTo(targetUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $validUserToken"))
            .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON))

        // When: 클라이언트 빈을 이용해 외부 모듈 호출
        internalRestClient.get()
            .uri(targetUri)
            .retrieve()
            .toBodilessEntity()

        // Then: 기대한 요청이 한 번도 빠짐없이 전달되었는지 상태를 검증
        mockServer.verify()
    }

    @Test
    @DisplayName("2. 비동기/스케줄러 환경(Request Context 부재)일 때: S2S 토큰이 대체 주입되는지 검증")
    fun shouldInjectS2STokenWhenRequestContextDoesNotExist() {
        // Given: 부모 Request가 없는 상태 (비동기 스레드나 스케줄러 환경과 동일)
        val expectedS2SToken = "S2S_INTERNAL_TRUSTED_TOKEN"
        every { tokenProvider.generateS2SToken() } returns expectedS2SToken
        val targetUri = "http://internal-service/api/v1/system-sync"

        // Mock Server: 해당 타겟 URL로 S2S(System) Token(Bearer) 헤더가 포함된 GET 요청이 올 것을 기대함
        mockServer.expect(requestTo(targetUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $expectedS2SToken"))
            .andRespond(withSuccess("{\"status\":\"synced\"}", MediaType.APPLICATION_JSON))

        // When: 현재 Context가 Null인 상태에서 클라이언트 빈 호출
        internalRestClient.get()
            .uri(targetUri)
            .retrieve()
            .toBodilessEntity()

        // Then: 예외 처리 분기를 통해 S2S 토큰이 정상적으로 심어졌는지 확인
        mockServer.verify()
    }

    @Test
    @DisplayName("3. 신뢰할 수 없는 외부(External) API 호출 시: 도메인 검증에 의해 토큰이 주입되지 않는지 검증")
    fun shouldNotPropagateTokenToExternalService() {
        // Given: 유저 토큰이 존재하는 환경
        val validUserToken = "SECRET_USER_TOKEN"
        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $validUserToken")
        requestHolder.set(request)

        // 대상이 신뢰할 수 없는 외부 도메인 (예: google.com)
        val externalUri = "https://www.google.com/api/v1/search"

        // Mock Server: Authorization 헤더가 '없어야' 함을 기대
        mockServer.expect(requestTo(externalUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect { mockRequest ->
                if (mockRequest.headers.containsHeader(HttpHeaders.AUTHORIZATION)) {
                    throw AssertionError("Authorization header should not be present for external services")
                }
            }
            .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON))

        // When
        internalRestClient.get()
            .uri(externalUri)
            .retrieve()
            .toBodilessEntity()

        // Then
        mockServer.verify()
    }
}

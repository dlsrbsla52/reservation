package com.media.bus.common.configuration;

import com.media.bus.common.security.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/// Spring Boot 4에서 `@RestClientTest`가 제거됨에 따라 `MockitoExtension` 기반 순수 단위 테스트로 전환.
/// `RestClient.Builder`에 `MockRestServiceServer`를 직접 바인딩하여
/// HTTP 인터셉터(토큰 전파) 동작을 Spring 컨텍스트 없이 검증한다.
@ExtendWith(MockitoExtension.class)
class RestClientConfigTest {

    /// `ObjectProvider<HttpServletRequest>`의 테스트 전용 구현체.
    /// 스레드 로컬로 요청을 저장하여 테스트가 독립적으로 컨텍스트를 제어할 수 있다.
    static class MockRequestHolder implements ObjectProvider<HttpServletRequest> {

        private final ThreadLocal<HttpServletRequest> local = new ThreadLocal<>();

        void set(HttpServletRequest req) { local.set(req); }
        void clear() { local.remove(); }

        @Override
        public HttpServletRequest getIfAvailable() { return local.get(); }

        @Override
        public HttpServletRequest getObject() {
            HttpServletRequest req = local.get();
            if (req == null) throw new IllegalStateException("요청 컨텍스트 없음");
            return req;
        }
    }

    private final MockRequestHolder requestHolder = new MockRequestHolder();

    @Mock
    private TokenProvider tokenProvider;

    private RestClient internalRestClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // MockRestServiceServer.bindTo(builder)로 목 서버를 바인딩한 뒤
        // 같은 builder를 RestClientConfig.buildWith()에 전달하여 인터셉터까지 적용된 RestClient를 조립한다.
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        internalRestClient = new RestClientConfig(tokenProvider, requestHolder).buildWith(builder);
        requestHolder.clear();
    }

    @AfterEach
    void tearDown() {
        requestHolder.clear();
    }

    @Test
    @DisplayName("1. 유저 요청(Request Context)이 존재할 때: HttpHeaders.AUTHORIZATION 이 정상적으로 전파되는지 검증")
    void shouldPropagateUserTokenWhenRequestContextExists() {
        // Given: 가상의 외부 유저 요청(HttpServletRequest) 환경 세팅
        String validUserToken = "VALID_USER_JWT_TOKEN";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken);
        requestHolder.set(request);

        String targetUri = "http://internal-service/api/v1/resource";

        // Mock Server: 해당 타겟 URL로 정상적인 User Token(Bearer) 헤더가 포함된 GET 요청이 올 것을 기대함
        mockServer.expect(requestTo(targetUri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        // When: 클라이언트 빈을 이용해 외부 모듈 호출
        internalRestClient.get()
                .uri(targetUri)
                .retrieve()
                .toBodilessEntity();

        // Then: 기대한 요청이 한 번도 빠짐없이 전달되었는지 상태를 검증
        mockServer.verify();
    }

    @Test
    @DisplayName("2. 비동기/스케줄러 환경(Request Context 부재)일 때: S2S 토큰이 대체 주입되는지 검증")
    void shouldInjectS2STokenWhenRequestContextDoesNotExist() {
        // Given: 부모 Request가 없는 상태 (비동기 스레드나 스케줄러 환경과 동일)
        // requestHolder는 setUp()에 의해 비어(Empty) 있습니다.
        String expectedS2SToken = "S2S_INTERNAL_TRUSTED_TOKEN";
        // Mock: JwtProvider가 generateS2SToken() 호출 시 고정된 토큰을 반환하도록 설정
        when(tokenProvider.generateS2SToken()).thenReturn(expectedS2SToken);
        String targetUri = "http://internal-service/api/v1/system-sync";

        // Mock Server: 해당 타겟 URL로 S2S(System) Token(Bearer) 헤더가 포함된 GET 요청이 올 것을 기대함
        mockServer.expect(requestTo(targetUri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + expectedS2SToken))
                .andRespond(withSuccess("{\"status\":\"synced\"}", MediaType.APPLICATION_JSON));

        // When: 현재 Context가 Null인 상태에서 클라이언트 빈 호출
        internalRestClient.get()
                .uri(targetUri)
                .retrieve()
                .toBodilessEntity();

        // Then: 예외 처리 분기를 통해 S2S 토큰이 정상적으로 심어졌는지 확인
        mockServer.verify();
    }

    @Test
    @DisplayName("3. 신뢰할 수 없는 외부(External) API 호출 시: 도메인 검증에 의해 토큰이 주입되지 않는지 검증")
    void shouldNotPropagateTokenToExternalService() {
        // Given: 유저 토큰이 존재하는 환경
        String validUserToken = "SECRET_USER_TOKEN";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken);
        requestHolder.set(request);

        // 대상이 신뢰할 수 없는 외부 도메인 (예: google.com)
        String externalUri = "https://www.google.com/api/v1/search";

        // Mock Server: Authorization 헤더가 '없어야' 함을 기대
        // Spring Framework 7에서 HttpHeaders.containsKey(String) → containsHeader(String)로 변경됨
        mockServer.expect(requestTo(externalUri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(mockRequest -> {
                    if (mockRequest.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                        throw new AssertionError("Authorization header should not be present for external services");
                    }
                })
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        // When
        internalRestClient.get()
                .uri(externalUri)
                .retrieve()
                .toBodilessEntity();

        // Then
        mockServer.verify();
    }
}
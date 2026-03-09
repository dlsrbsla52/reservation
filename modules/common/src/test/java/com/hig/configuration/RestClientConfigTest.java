package com.hig.configuration;

import com.hig.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest
@Import(RestClientConfig.class)
class RestClientConfigTest {

    // @RestClientTest는 슬라이스 테스트이므로 Redis, JPA 등의 인프라 Bean을 로드하지 않는다.
    // JwtProvider는 StringRedisTemplate과 ${jwt.secret}에 의존하므로
    // 슬라이스 컨텍스트에서 직접 생성이 불가하여 @MockitoBean으로 대체한다.
    @MockitoBean
    private JwtProvider jwtProvider;

    @Autowired
    private RestClient internalRestClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // 테스트 전 RequestContext 초기화 (격리 보장)
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 메모리 누수 방지용 초기화
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("1. 유저 요청(Request Context)이 존재할 때: HttpHeaders.AUTHORIZATION 이 정상적으로 전파되는지 검증")
    void shouldPropagateUserTokenWhenRequestContextExists() {
        // Given: 가상의 외부 유저 요청(HttpServletRequest) 환경 세팅
        String validUserToken = "VALID_USER_JWT_TOKEN";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + validUserToken);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

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
        // RequestContextHolder 는 setUp() 에 의해 비어(Empty) 있습니다.
        String expectedS2SToken = "S2S_INTERNAL_TRUSTED_TOKEN";
        // Mock: JwtProvider가 generateS2SToken() 호출 시 고정된 토큰을 반환하도록 설정
        when(jwtProvider.generateS2SToken()).thenReturn(expectedS2SToken);
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
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 대상이 신뢰할 수 없는 외부 도메인 (예: google.com)
        String externalUri = "https://www.google.com/api/v1/search";

        // Mock Server: Authorization 헤더가 '없어야' 함을 기대
        mockServer.expect(requestTo(externalUri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(mockRequest -> {
                    if (mockRequest.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
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

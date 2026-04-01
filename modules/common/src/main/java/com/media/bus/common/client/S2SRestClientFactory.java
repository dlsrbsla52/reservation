package com.media.bus.common.client;

import com.media.bus.common.security.TokenProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/// S2S(System-to-System) 전용 RestClient 팩토리.
/// 설계 의도:
/// - internalRestClient는 유저 컨텍스트가 있으면 유저 JWT를 전파하므로
///   순수 S2S 호출에는 부적합합니다.
/// - 이 팩토리는 항상 X-Service-Token 헤더에 S2S JWT를 주입하여
///   대상 서비스의 S2STokenFilter를 통과할 수 있는 RestClient를 생성합니다.
/// - 호출마다 토큰을 새로 생성하여 토큰 만료 위험을 최소화합니다.
/// - @Component 없이 plain class로 선언. Bean 등록은 RestClientConfig에서 담당합니다.
public class S2SRestClientFactory {

    private static final String S2S_TOKEN_HEADER = "X-Service-Token";

    private final TokenProvider tokenProvider;

    public S2SRestClientFactory(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /// 지정된 baseUrl과 서비스 인터페이스 타입으로 @HttpExchange 프록시를 생성합니다.
    /// S2S 토큰 자동 주입이 적용된 RestClient를 기반으로 프록시가 동작합니다.
    ///
    /// @param baseUrl          대상 서비스 베이스 URL (예: http://stop-service:8182)
    /// @param serviceInterface @HttpExchange가 선언된 인터페이스 타입
    /// @return S2S 토큰 인터셉터가 적용된 @HttpExchange 프록시 객체
    public <T> T createProxy(String baseUrl, Class<T> serviceInterface) {
        RestClientAdapter adapter = RestClientAdapter.create(create(baseUrl));
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(serviceInterface);
    }

    /// 지정된 baseUrl을 대상으로 S2S 토큰을 자동 주입하는 RestClient를 생성합니다.
    ///
    /// @param baseUrl 대상 서비스 베이스 URL (예: http://stop-service:8182)
    /// @return X-Service-Token 헤더를 자동 주입하는 RestClient
    public RestClient create(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                // 요청마다 신규 S2S 토큰 발급 후 헤더 주입 (람다는 매 요청 시 실행됨)
                .requestInterceptor((req, body, exec) -> {
                    req.getHeaders().add(S2S_TOKEN_HEADER, tokenProvider.generateS2SToken());
                    return exec.execute(req, body);
                })
                .build();
    }
}

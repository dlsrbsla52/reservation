package com.media.bus.common.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/// S2S 서비스 클라이언트 공통 추상 클래스.
/// 설계 의도:
/// - GET / POST / PUT / DELETE 메서드를 REST 규칙에 맞게 사전 정의하여
///   하위 클라이언트가 HTTP 레이어를 반복 구현하지 않도록 한다.
/// - RestClient는 생성자에서 주입받아 S2S / 일반 클라이언트 모두 재사용 가능하다.
///   (S2S 호출 시 하위 클래스가 S2SRestClientFactory.create()로 생성하여 super()에 전달)
/// - 응답 역직렬화 타입은 제네릭으로 지정하여 타입 안전성을 확보한다.
public abstract class BaseServiceClient {

    protected final RestClient restClient;

    /// @param restClient 사전 구성된 RestClient (인터셉터 포함)
    protected BaseServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    /// 쿼리 파라미터 등 URI를 동적으로 구성해야 하는 GET 요청.
    ///
    /// @param uriFunction UriBuilder를 받아 URI를 반환하는 람다
    /// @param responseType 응답 역직렬화 타입
    protected <T> T get(Function<UriBuilder, URI> uriFunction, Class<T> responseType) {
        return restClient.get()
                .uri(uriFunction)
                .retrieve()
                .body(responseType);
    }

    /// 고정 경로에 대한 단순 GET 요청.
    ///
    /// @param path 요청 경로 (예: /api/v1/internal/stop/123)
    /// @param responseType 응답 역직렬화 타입
    protected <T> T get(String path, Class<T> responseType) {
        return restClient.get()
                .uri(path)
                .retrieve()
                .body(responseType);
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    /// 요청 바디가 있는 POST 요청.
    ///
    /// @param path 요청 경로
    /// @param body 직렬화할 요청 바디 객체
    /// @param responseType 응답 역직렬화 타입
    protected <T> T post(String path, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    /// 응답 바디가 없는 POST 요청 (201 Created 등).
    ///
    /// @param path 요청 경로
    /// @param body 직렬화할 요청 바디 객체
    protected void post(String path, Object body) {
        restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    /// 요청 바디가 있는 PUT 요청.
    ///
    /// @param path 요청 경로
    /// @param body 직렬화할 요청 바디 객체
    /// @param responseType 응답 역직렬화 타입
    protected <T> T put(String path, Object body, Class<T> responseType) {
        return restClient.put()
                .uri(path)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    /// 응답 바디가 없는 PUT 요청 (204 No Content 등).
    ///
    /// @param path 요청 경로
    /// @param body 직렬화할 요청 바디 객체
    protected void put(String path, Object body) {
        restClient.put()
                .uri(path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    /// 고정 경로에 대한 DELETE 요청.
    ///
    /// @param path 요청 경로
    protected void delete(String path) {
        restClient.delete()
                .uri(path)
                .retrieve()
                .toBodilessEntity();
    }

    /// 응답 바디가 있는 DELETE 요청.
    ///
    /// @param path 요청 경로
    /// @param responseType 응답 역직렬화 타입
    protected <T> T delete(String path, Class<T> responseType) {
        return restClient.delete()
                .uri(path)
                .retrieve()
                .body(responseType);
    }
}

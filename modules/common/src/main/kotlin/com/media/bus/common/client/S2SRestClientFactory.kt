package com.media.bus.common.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.security.TokenProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * ## S2S(System-to-System) 전용 RestClient 팩토리
 *
 * 설계 의도:
 * - internalRestClient는 유저 컨텍스트가 있으면 유저 JWT를 전파하므로
 *   순수 S2S 호출에는 부적합하다.
 * - 이 팩토리는 항상 X-Service-Token 헤더에 S2S JWT를 주입하여
 *   대상 서비스의 S2STokenFilter를 통과할 수 있는 RestClient를 생성한다.
 * - 호출마다 토큰을 새로 생성하여 토큰 만료 위험을 최소화한다.
 * - @Component 없이 plain class로 선언. Bean 등록은 RestClientConfig에서 담당한다.
 */
class S2SRestClientFactory(private val tokenProvider: TokenProvider) {

    companion object {
        private const val S2S_TOKEN_HEADER = "X-Service-Token"
        private val log = LoggerFactory.getLogger(S2SRestClientFactory::class.java)
        private val objectMapper = ObjectMapper()
    }

    /**
     * 지정된 baseUrl과 서비스 인터페이스 타입으로 @HttpExchange 프록시를 생성한다.
     * S2S 토큰 자동 주입이 적용된 RestClient를 기반으로 프록시가 동작한다.
     *
     * @param baseUrl 대상 서비스 베이스 URL (예: http://stop-service:8182)
     * @param serviceInterface @HttpExchange가 선언된 인터페이스 타입
     * @return S2S 토큰 인터셉터가 적용된 @HttpExchange 프록시 객체
     */
    fun <T : Any> createProxy(baseUrl: String, serviceInterface: Class<T>): T {
        val adapter = RestClientAdapter.create(create(baseUrl))
        val factory = HttpServiceProxyFactory.builderFor(adapter).build()
        return factory.createClient(serviceInterface)
    }

    /**
     * 지정된 baseUrl을 대상으로 S2S 토큰을 자동 주입하는 RestClient를 생성한다.
     *
     * @param baseUrl 대상 서비스 베이스 URL (예: http://stop-service:8182)
     * @return X-Service-Token 헤더를 자동 주입하는 RestClient
     */
    fun create(baseUrl: String): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            // 요청마다 신규 S2S 토큰 발급 후 헤더 주입 (람다는 매 요청 시 실행됨)
            .requestInterceptor { req, body, exec ->
                req.headers.add(S2S_TOKEN_HEADER, tokenProvider.generateS2SToken())
                exec.execute(req, body)
            }
            // 하위 서비스의 4xx 를 호출자에게 같은 상태로 전달한다.
            // 처리기가 없으면 HttpClientErrorException 이 글로벌 핸들러의 catch-all 로 떨어져
            // 중복(409)·검증 실패(400) 같은 정상 비즈니스 실패가 모두 500 으로 보인다.
            .defaultStatusHandler(HttpStatusCode::is4xxClientError) { req, res ->
                throw translateClientError(req.method.name(), req.uri.path, res)
            }
            .build()

    /**
     * 하위 서비스 4xx 응답을 공통 예외로 변환한다.
     *
     * - 401/403 은 **사용자 인증 문제가 아니라 S2S 토큰·설정 문제**다. 그대로 넘기면 프론트가
     *   자기 토큰이 만료된 줄 알고 갱신·로그아웃을 시도하므로 500(`ServiceException`)으로 바꾼다.
     * - 그 외 4xx 는 같은 HTTP 상태의 `BusinessException` 으로, 하위 응답의 `code`·`message` 를 그대로 싣는다.
     *   코드를 버리면 호출자(관리자 화면)가 `R0010`·`C0004` 같은 실패 사유를 메시지 문자열로만 구분해야 한다.
     */
    private fun translateClientError(method: String, path: String, res: ClientHttpResponse): RuntimeException {
        val status = res.statusCode.value()
        val body = readBody(res)
        val downstreamCode = body.text("code")
        val downstreamMessage = body.text("message")
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            log.error("[S2S] 하위 서비스 인증 거부 — S2S 토큰/설정 확인 필요. {} {} status={}", method, path, status)
            return ServiceException(CommonResult.SERVICE_ERROR, "내부 서비스 인증에 실패했습니다.")
        }
        log.warn(
            "[S2S] 하위 서비스 요청 거부. {} {} status={} code={} message={}",
            method, path, status, downstreamCode, downstreamMessage,
        )
        val httpStatus = HttpStatus.resolve(status) ?: HttpStatus.BAD_REQUEST
        val message = downstreamMessage ?: "요청을 처리할 수 없습니다."
        // 하위 코드가 없으면(본문 없음·비 JSON) 기존처럼 공통 실패 코드로 응답한다.
        val result = downstreamCode?.let { DownstreamResult(it, message, httpStatus) } ?: CommonResult.FAIL
        return BusinessException(result = result, message = message, httpStatus = httpStatus)
    }

    /** 하위 응답 본문(`ApiResponse`/`ErrorView`)을 읽는다. 본문이 없거나 JSON 이 아니면 null. */
    private fun readBody(res: ClientHttpResponse): JsonNode? =
        runCatching { objectMapper.readTree(res.body) }.getOrNull()

    private fun JsonNode?.text(field: String): String? =
        this?.get(field)?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
}

/**
 * ## 하위 서비스가 돌려준 결과 코드를 그대로 전달하기 위한 `Result`
 *
 * 모듈 경계상 호출자는 하위 모듈의 Result enum(`ReservationResult` 등)을 참조할 수 없으므로,
 * 응답 본문의 `code`·`message`·HTTP 상태만 담아 `ExceptionAdvisor`가 그대로 내보내게 한다.
 */
data class DownstreamResult(
    override val code: String,
    override val message: String,
    private val status: HttpStatus,
) : Result {
    override fun httpStatus(): HttpStatus = status
}

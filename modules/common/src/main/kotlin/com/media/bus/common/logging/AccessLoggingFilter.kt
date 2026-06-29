package com.media.bus.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * ## HTTP 요청/응답 액세스 로그 필터
 *
 * 요청 진입 시 `START` 한 줄, 완료 시 `ACCESS` 한 줄(INFO)을 남긴다.
 * `ACCESS` 는 예외 발생 여부와 무관하게(`finally` 블록) 응답 코드와 소요 시간을 기록한다.
 * 동시 요청이 콘솔에 섞여도 MDC `[req:...]` 로 묶으면 한 요청의 `START ~ ACCESS` 흐름이 시간순으로 보인다.
 *
 * 출력 포맷:
 *   `START GET /api/v1/member/find/me`                                        (요청 진입)
 *   `ACCESS GET /api/v1/member/find/me -> 200 (12ms)`                         (성공: 본문 미기록)
 *   `ACCESS GET /api/v1/member/find/me -> 400 (12ms) res={"code":"...","message":"..."}`  (에러: 본문 기록)
 *   `ACCESS POST /api/v1/auth/login -> 200 (45ms) req={"username":"a","password":"***"} res={"data":{"accessToken":"***"}}`  (DEBUG 토글 시)
 *
 * ### 본문 기록 정책 (비용 제어)
 * 단일 호스트 + 저예산 운영 환경을 고려하여, 성공 응답 본문은 평소 기록하지 않는다.
 * 본문은 **다음 두 조건 중 하나일 때만** `req=`/`res=`로 남긴다.
 *   1. **HTTP 상태 >= 400** — 실패는 원인 추적을 위해 항상 본문을 남긴다.
 *   2. **본 로거가 DEBUG 레벨** — 평상시엔 OFF, 조사할 때만 켠다(무재배포 토글).
 *      `POST /actuator/loggers/com.media.bus.common.logging.AccessLoggingFilter {"configuredLevel":"DEBUG"}`
 * 그 외 성공 응답은 `ACCESS ... -> 200 (ms)` 한 줄만 남겨 로그량/디스크/PII 노출을 최소화한다.
 * (ACCESS 라인 자체는 항상 INFO로 남으므로, 토글과 무관하게 모든 요청의 진입/상태/지연은 추적된다.)
 *
 * 보안 처리:
 *   - `password`, `token`, `refreshToken`, `accessToken` 필드는 JSON/폼/쿼리스트링 어디에 있든 `***`로 마스킹
 *   - 요청/응답 본문은 각각 최대 2KB까지만 잘라서 기록 (대용량 페이로드로 인한 로그 폭증 방지)
 *   - `multipart/...`, `application/octet-stream` 등 바이너리 콘텐츠 타입은 본문 로깅 제외
 *   - 응답은 JSON/텍스트 계열만 본문을 기록한다 (파일 다운로드 등 바이너리 스트림 보호)
 *
 * 응답 본문 캡처:
 *   [ContentCachingResponseWrapper]로 응답을 감싸 컨트롤러가 쓴 바이트를 버퍼에 보관한 뒤,
 *   로그를 남기고 **반드시 `copyBodyToResponse()`로 실제 응답에 다시 흘려보낸다**.
 *   (이 호출을 빼면 클라이언트로 본문이 전송되지 않으니 주의)
 *   주의: 응답 본문은 (기록 여부와 무관하게) 요청 처리 동안 메모리에 버퍼링된다 — in-flight 요청당 일시적이며
 *   `copyBodyToResponse()` 직후 해제된다. 알려진 대용량/스트리밍 엔드포인트는 [shouldNotFilter]에 추가해 제외할 것.
 *
 * MDC(`requestId`, `memberId`, `traceId`/`spanId`)는 [MdcLoggingFilter]가 먼저 주입한 값을
 * 그대로 사용한다. 따라서 본 필터의 order는 [MdcLoggingFilter]보다 커야 한다.
 *
 * 헬스체크/Actuator 트래픽은 로그 노이즈를 줄이기 위해 제외한다.
 */
class AccessLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AccessLoggingFilter::class.java)

    companion object {
        // 마스킹 대상 필드명 (대소문자 무시). 추가 시 정규식이 동적으로 반영된다.
        // accessToken: 로그인/토큰 갱신 응답 본문에 노출되므로 응답 마스킹을 위해 포함한다.
        private val SENSITIVE_FIELDS = listOf("password", "accessToken", "refreshToken", "token")
        private const val MASKED = "***"
        private const val MAX_BODY_LENGTH = 2048

        // 본문이 있을 수 있는 메서드만 요청 본문 캐싱 대상
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

        // 응답 본문을 기록할 콘텐츠 타입(텍스트 계열) 접두사. 그 외(이미지/바이너리/스트림)는 제외.
        private val LOGGABLE_RESPONSE_TYPES = listOf("application/json", "text/", "application/xml")

        // JSON 형식: "password":"abc" → "password":"***"
        // 따옴표 사이에 escape된 quote가 있으면 매칭 실패하지만, password/token류는 통상 단순 문자열이라 허용 가능.
        // (raw string은 따옴표로 끝낼 수 없어 일반 문자열 + escape 사용)
        private val JSON_FIELD_PATTERN = Regex(
            "\"(${SENSITIVE_FIELDS.joinToString("|")})\"\\s*:\\s*\"([^\"]*)\"",
            RegexOption.IGNORE_CASE,
        )

        // x-www-form-urlencoded / 쿼리스트링 형식: password=abc&... → password=***
        // (?<=^|&) lookbehind 로 단어 경계에서만 매칭하여 다른 키의 접미사가 잘못 잡히는 것을 방지.
        private val FORM_FIELD_PATTERN = Regex(
            """(?<=^|&)(${SENSITIVE_FIELDS.joinToString("|")})=([^&]*)""",
            RegexOption.IGNORE_CASE,
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 본문 캐싱이 필요한 요청에만 wrapper를 씌워 메모리 부담을 최소화한다.
        // 컨트롤러가 InputStream을 1회 읽고 나면 원본은 비므로, 로깅 측에서 본문을 보려면 캐싱 필수.
        // cacheLimit: 본문 캐싱 최대 바이트. 로그 노출 한도와 일치시켜 불필요한 메모리 보유를 막는다.
        val wrappedRequest =
            if (shouldCacheRequestBody(request)) ContentCachingRequestWrapper(request, MAX_BODY_LENGTH) else null
        val effectiveRequest: HttpServletRequest = wrappedRequest ?: request

        // 응답은 본문을 읽으려면 항상 캐싱해야 한다 (컨트롤러가 쓴 직후엔 OutputStream으로 흘러가 버림).
        val wrappedResponse = ContentCachingResponseWrapper(response)

        // 요청 진입 마커. 동시 요청이 콘솔에 섞여도 `[req:...]` 로 묶으면 START~ACCESS 구간이 명확해진다.
        // (ACCESS 는 소요시간/상태를 측정해 finally 에서 마지막에 남으므로, 시작 지점은 별도 마커가 필요)
        log.info("START {} {}", request.method, buildPath(request))

        val startNanos = System.nanoTime()
        try {
            filterChain.doFilter(effectiveRequest, wrappedResponse)
        } finally {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val status = wrappedResponse.status

            // 본문 기록 여부: 에러(>=400)이거나 DEBUG 토글이 켜졌을 때만. 평상시 성공은 한 줄만.
            val includeBody = status >= 400 || log.isDebugEnabled
            val reqSnippet = if (includeBody) wrappedRequest?.let { extractRequestBody(it) }.orEmpty() else ""
            val resSnippet = if (includeBody) extractResponseBody(wrappedResponse) else ""

            log.info(
                "ACCESS {} {} -> {} ({}ms){}{}",
                request.method,
                buildPath(request),
                status,
                elapsedMs,
                if (reqSnippet.isBlank()) "" else " req=$reqSnippet",
                if (resSnippet.isBlank()) "" else " res=$resSnippet",
            )

            // 캐싱된 응답 본문을 실제 응답으로 복사한다. 누락 시 클라이언트가 빈 본문을 받게 되므로 필수.
            wrappedResponse.copyBodyToResponse()
        }
    }

    /**
     * 헬스체크/모니터링 엔드포인트는 제외한다.
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI ?: return false
        return uri.startsWith("/actuator") || uri.startsWith("/health")
    }

    /**
     * 요청 본문을 캐싱할 가치가 있는 요청인지 판단.
     *
     * - GET/DELETE/HEAD/OPTIONS: 본문이 없거나 의미 없음
     * - multipart/octet-stream: 파일 업로드 등 대용량 바이너리 → 메모리 폭증 위험
     */
    private fun shouldCacheRequestBody(request: HttpServletRequest): Boolean {
        if (request.method.uppercase() !in METHODS_WITH_BODY) return false
        val contentType = request.contentType?.lowercase().orEmpty()
        return !contentType.startsWith("multipart/") &&
            !contentType.startsWith("application/octet-stream")
    }

    /**
     * 캐싱된 요청 본문에서 마스킹된 스니펫을 추출한다.
     *
     * 컨트롤러가 InputStream을 읽은 시점에만 [ContentCachingRequestWrapper]가 바이트를 보유한다.
     * (필터 체인이 끝난 finally 시점에 호출되므로 통상 채워져 있음)
     */
    private fun extractRequestBody(wrapper: ContentCachingRequestWrapper): String =
        snippetOf(wrapper.contentAsByteArray, wrapper.characterEncoding)

    /**
     * 캐싱된 응답 본문에서 마스킹된 스니펫을 추출한다.
     * JSON/텍스트 계열 콘텐츠 타입만 기록하여 바이너리 스트림(파일 다운로드 등)을 보호한다.
     */
    private fun extractResponseBody(wrapper: ContentCachingResponseWrapper): String {
        val contentType = wrapper.contentType?.lowercase().orEmpty()
        if (LOGGABLE_RESPONSE_TYPES.none { contentType.startsWith(it) }) return ""
        return snippetOf(wrapper.contentAsByteArray, wrapper.characterEncoding)
    }

    /**
     * 바이트 본문을 한 줄 + 마스킹 + 길이 제한된 로그 스니펫으로 변환하는 공통 로직.
     * 요청/응답 본문이 동일한 규칙으로 처리되도록 하나로 모았다.
     */
    private fun snippetOf(bytes: ByteArray, @Suppress("UNUSED_PARAMETER") encoding: String?): String {
        if (bytes.isEmpty()) return ""

        // Content-Type에 charset이 없으면 서블릿 기본값(ISO-8859-1)이 반환되어 한글이 깨지므로 항상 UTF-8로 고정
        val raw = String(bytes, Charsets.UTF_8)

        // 줄바꿈/탭은 한 줄 로그로 흡수되어야 가독성이 좋음
        val singleLine = raw.replace(Regex("\\s+"), " ").trim()
        val masked = maskSensitive(singleLine)

        return if (masked.length > MAX_BODY_LENGTH) {
            masked.substring(0, MAX_BODY_LENGTH) + "...(truncated)"
        } else {
            masked
        }
    }

    /**
     * JSON 및 form-urlencoded 두 가지 포맷의 민감 필드를 모두 마스킹.
     * 두 패턴을 순차 적용하므로 혼합 페이로드(예: JSON 내 일부가 url-encoded)도 보호.
     */
    private fun maskSensitive(body: String): String {
        val afterJson = JSON_FIELD_PATTERN.replace(body) { match ->
            val key = match.groupValues[1]
            "\"$key\":\"$MASKED\""
        }
        return FORM_FIELD_PATTERN.replace(afterJson) { match ->
            val key = match.groupValues[1]
            "$key=$MASKED"
        }
    }

    private fun buildPath(request: HttpServletRequest): String {
        val uri = request.requestURI
        val query = request.queryString
        return if (query.isNullOrBlank()) uri else "$uri?${maskQueryString(query)}"
    }

    private fun maskQueryString(query: String): String =
        FORM_FIELD_PATTERN.replace(query) { match ->
            val key = match.groupValues[1]
            "$key=$MASKED"
        }
}

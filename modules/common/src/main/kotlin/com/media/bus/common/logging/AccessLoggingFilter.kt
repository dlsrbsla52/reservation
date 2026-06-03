package com.media.bus.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import java.nio.charset.Charset

/**
 * ## HTTP 요청/응답 액세스 로그 필터
 *
 * 모든 요청 완료 시점에 한 줄 INFO 로그를 남긴다.
 * 예외 발생 여부와 무관하게(`finally` 블록) 응답 코드와 소요 시간을 기록하므로,
 * 어떤 컨트롤러/엔드포인트로 요청이 들어왔는지 정상 흐름에서도 추적할 수 있다.
 *
 * 출력 포맷:
 *   `ACCESS GET /api/v1/member/find/me -> 400 (12ms)`
 *   `ACCESS POST /api/v1/auth/login -> 200 (45ms) body={"username":"a","password":"***"}`
 *
 * 보안 처리:
 *   - `password`, `token`, `refreshToken` 필드는 JSON/폼/쿼리스트링 어디에 있든 `***`로 마스킹
 *   - 본문은 최대 2KB까지만 잘라서 기록 (대용량 페이로드로 인한 로그 폭증 방지)
 *   - `multipart/...`, `application/octet-stream` 등 바이너리/대용량 콘텐츠 타입은 본문 로깅 제외
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
        private val SENSITIVE_FIELDS = listOf("password", "token", "refreshToken")
        private const val MASKED = "***"
        private const val MAX_BODY_LENGTH = 2048

        // 본문이 있을 수 있는 메서드만 캐싱 대상
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

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
        val wrapped = if (shouldCacheBody(request)) ContentCachingRequestWrapper(request, MAX_BODY_LENGTH) else null
        val effective: HttpServletRequest = wrapped ?: request

        val startNanos = System.nanoTime()
        try {
            filterChain.doFilter(effective, response)
        } finally {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val bodySnippet = wrapped?.let { extractBody(it) }.orEmpty()
            log.info(
                "ACCESS {} {} -> {} ({}ms){}",
                request.method,
                buildPath(request),
                response.status,
                elapsedMs,
                if (bodySnippet.isBlank()) "" else " body=$bodySnippet",
            )
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
     * 본문을 캐싱할 가치가 있는 요청인지 판단.
     *
     * - GET/DELETE/HEAD/OPTIONS: 본문이 없거나 의미 없음
     * - multipart/octet-stream: 파일 업로드 등 대용량 바이너리 → 메모리 폭증 위험
     */
    private fun shouldCacheBody(request: HttpServletRequest): Boolean {
        if (request.method.uppercase() !in METHODS_WITH_BODY) return false
        val contentType = request.contentType?.lowercase().orEmpty()
        return !contentType.startsWith("multipart/") &&
            !contentType.startsWith("application/octet-stream")
    }

    /**
     * 캐싱된 본문에서 마스킹된 스니펫을 추출한다.
     *
     * 컨트롤러가 InputStream을 읽은 시점에만 [ContentCachingRequestWrapper]가 바이트를 보유한다.
     * (필터 체인이 끝난 finally 시점에 호출되므로 통상 채워져 있음)
     */
    private fun extractBody(wrapper: ContentCachingRequestWrapper): String {
        val bytes = wrapper.contentAsByteArray
        if (bytes.isEmpty()) return ""

        val charset = runCatching { Charset.forName(wrapper.characterEncoding) }
            .getOrDefault(Charsets.UTF_8)
        val raw = String(bytes, charset)

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

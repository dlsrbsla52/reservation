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
 *   `START POST /api/v1/member/find/me`                                        (요청 진입)
 *   `ACCESS POST /api/v1/member/find/me -> 200 (12ms)`                         (성공: 본문 미기록)
 *   `ACCESS POST /api/v1/member/find/me -> 400 (12ms) res={"code":"...","message":"..."}`  (에러: 본문 기록)
 *   `ACCESS POST /api/v1/auth/login -> 200 (45ms) req={"username":"a","password":"***"} res={"data":{"accessToken":"***"}}`  (DEBUG 토글 시)
 *
 * ### 본문 기록 정책 (비용 제어)
 * 단일 호스트 + 저예산 운영 환경을 고려하여, 성공 응답 본문은 평소 기록하지 않는다.
 * 본문은 **다음 두 조건 중 하나일 때만** `req=`/`res=`로 남긴다.
 *   1. **HTTP 상태 >= 400** — 실패는 원인 추적을 위해 항상 본문을 남긴다.
 *   2. **본 로거가 DEBUG 레벨** — 평상시엔 OFF, 조사할 때만 켠다(무재배포 토글).
 *      `POST /actuator/loggers/com.media.bus.common.logging.AccessLoggingFilter {"configuredLevel":"DEBUG"}`
 * 그 외 성공 응답은 `ACCESS ... -> 200 (ms)` 한 줄만 남겨 로그량/디스크/PII 노출을 최소화한다.
 *
 * ### 본문 기록 제외 경로 (개인정보)
 * 이름·연락처·이메일·문의 내용이 오가는 경로는 4xx 이거나 DEBUG 여도 요청·응답 본문을 남기지 않는다([isBodyLogExcluded]).
 *   - `/api/v1/member/find/me` (아이디 찾기)
 *   - `/api/v1/content/inquiry` (공개 온라인 문의 접수)
 *   - `/api/v1/admin/content/inquiry` 와 그 하위 (관리자 문의 조회·처리·삭제 — 응답에 문의자 정보가 들어 있다)
 * (ACCESS 라인 자체는 항상 INFO로 남으므로, 토글과 무관하게 모든 요청의 진입/상태/지연은 추적된다.)
 *
 * 보안 처리:
 *   - 필드명에 `password`·`token`이 들어간 필드(`newPassword`, `currentPassword`, `accessToken`, `refreshToken` 등)는
 *     JSON/폼/쿼리스트링 어디에 있든 `***`로 마스킹(대소문자 무시)
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
 * MDC(`requestId`, `memberId`)는 [MdcLoggingFilter]가 먼저 주입한 값을
 * 그대로 사용한다. 따라서 본 필터의 order는 [MdcLoggingFilter]보다 커야 한다.
 *
 * 헬스체크/Actuator 트래픽은 로그 노이즈를 줄이기 위해 제외한다.
 */
class AccessLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AccessLoggingFilter::class.java)

    companion object {
        // 마스킹 대상 키워드 (대소문자 무시). 필드명에 키워드가 **포함**되면 마스킹한다.
        // 정확 일치로 두면 newPassword·currentPassword(비밀번호 변경)처럼 변형된 필드명이 평문으로 남는다.
        // token 하나로 accessToken·refreshToken(로그인/갱신 응답)도 가린다. tokenType 처럼 민감하지 않은 필드도
        // 함께 가려지지만, 새 필드를 놓쳐 비밀번호가 남는 쪽보다 낫다.
        private val SENSITIVE_KEYWORDS = listOf("password", "token")
        private const val MASKED = "***"
        private const val MAX_BODY_LENGTH = 2048

        // 본문(요청·응답)을 로그에 남기지 않을 경로. 정확히 같거나 그 하위 경로면 제외한다.
        private val BODY_LOG_EXCLUDED_PATHS = listOf(
            "/api/v1/member/find/me",
            "/api/v1/content/inquiry",
            "/api/v1/admin/content/inquiry",
        )

        // 본문이 있을 수 있는 메서드만 요청 본문 캐싱 대상
        private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

        // 응답 본문을 기록할 콘텐츠 타입(텍스트 계열) 접두사. 그 외(이미지/바이너리/스트림)는 제외.
        private val LOGGABLE_RESPONSE_TYPES = listOf("application/json", "text/", "application/xml")

        // 키 인식과 민감 여부 판단을 분리하여 긴 키에서 정규식 백트래킹을 막는다.
        private val JSON_FIELD_PATTERN = Regex("\"([A-Za-z0-9_-]++)\"\\s*+:\\s*+")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 본문 캐싱이 필요한 요청에만 wrapper를 씌워 메모리 부담을 최소화한다.
        // 컨트롤러가 InputStream을 1회 읽고 나면 원본은 비므로, 로깅 측에서 본문을 보려면 캐싱 필수.
        // cacheLimit: 본문 캐싱 최대 바이트. 로그 노출 한도와 일치시켜 불필요한 메모리 보유를 막는다.
        val bodyLogExcluded = isBodyLogExcluded(request)
        val wrappedRequest =
            if (!bodyLogExcluded && shouldCacheRequestBody(request)) ContentCachingRequestWrapper(request, MAX_BODY_LENGTH) else null
        val effectiveRequest: HttpServletRequest = wrappedRequest ?: request

        // 응답은 본문을 읽으려면 항상 캐싱해야 한다 (컨트롤러가 쓴 직후엔 OutputStream으로 흘러가 버림).
        val wrappedResponse = ContentCachingResponseWrapper(response)

        // 요청 진입 마커. 동시 요청이 콘솔에 섞여도 `[req:...]` 로 묶으면 START~ACCESS 구간이 명확해진다.
        // (ACCESS 는 소요시간/상태를 측정해 finally 에서 마지막에 남으므로, 시작 지점은 별도 마커가 필요)
        val path = buildPath(request)
        log.info("START {} {}", request.method, path)

        val startNanos = System.nanoTime()
        try {
            filterChain.doFilter(effectiveRequest, wrappedResponse)
        } finally {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val status = wrappedResponse.status

            // 본문 기록 여부: 에러(>=400)이거나 DEBUG 토글이 켜졌을 때만. 평상시 성공은 한 줄만.
            val includeBody = !bodyLogExcluded && (status >= 400 || log.isDebugEnabled)
            val reqSnippet = if (includeBody) wrappedRequest?.let { extractRequestBody(it) }.orEmpty() else ""
            val resSnippet = if (includeBody) extractResponseBody(wrappedResponse) else ""

            log.info(
                "ACCESS {} {} -> {} ({}ms){}{}",
                request.method,
                path,
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

    /** 개인정보가 오가는 경로라 DEBUG·4xx 에서도 요청·응답 본문을 기록하지 않는지 판단한다. */
    private fun isBodyLogExcluded(request: HttpServletRequest): Boolean {
        val uri = request.requestURI ?: return false
        return BODY_LOG_EXCLUDED_PATHS.any { uri == it || uri.startsWith("$it/") }
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
     * 테스트에서 마스킹 규칙을 직접 검증하도록 `internal`로 둔다.
     */
    internal fun maskSensitive(body: String): String {
        val result = StringBuilder(body.length)
        var cursor = 0
        var match = JSON_FIELD_PATTERN.find(body)
        while (match != null) {
            if (isSensitiveKey(match.groupValues[1])) {
                result.append(body, cursor, match.range.first)
                result.append('"').append(match.groupValues[1]).append("\":\"$MASKED\"")
                cursor = jsonValueEnd(body, match.range.last + 1)
                match = JSON_FIELD_PATTERN.find(body, cursor)
            } else {
                match = match.next()
            }
        }
        result.append(body, cursor, body.length)
        return maskQueryString(result.toString())
    }

    /** 닫히지 않은 문자열은 캐시 끝까지 가린다. 객체·배열도 값 전체를 한 번만 순회한다. */
    private fun jsonValueEnd(body: String, start: Int): Int {
        var quoted = false
        var escaped = false
        var depth = 0
        for (index in start until body.length) {
            val char = body[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') {
                    quoted = false
                    if (depth == 0) return index + 1
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        if (depth == 0) return index
                        depth--
                        if (depth == 0) return index + 1
                    }
                    ',' -> if (depth == 0) return index
                }
            }
        }
        return body.length
    }

    private fun isSensitiveKey(key: String): Boolean =
        SENSITIVE_KEYWORDS.any { key.contains(it, ignoreCase = true) }

    private fun buildPath(request: HttpServletRequest): String {
        val uri = request.requestURI
        val query = request.queryString
        return if (query.isNullOrBlank()) uri else "$uri?${maskQueryString(query)}"
    }

    internal fun maskQueryString(query: String): String =
        query.split('&').joinToString("&") { field ->
            val separator = field.indexOf('=')
            if (separator < 0) field
            else {
                val key = field.substring(0, separator)
                val decodedKey = try {
                    java.net.URLDecoder.decode(key, Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    key
                }
                if (isSensitiveKey(decodedKey)) "$key=$MASKED" else field
            }
        }
}

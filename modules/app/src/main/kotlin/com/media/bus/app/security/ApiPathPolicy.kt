package com.media.bus.app.security

/**
 * ## 공개 포트(8080) 경로 정책
 *
 * 예전 gateway가 하던 두 가지를 앱 안에서 다시 만든다.
 *
 * - **라우트 허용 목록**: gateway는 정해진 prefix만 하위 서비스로 보냈다. 모놀리스는 앱의 모든 핸들러가 8080에 열리므로,
 *   목록 밖 경로(`/error`, `/actuator` 하위 등)는 [isRoutable]에서 걸러 404로 끝낸다.
 * - **공개 엔드포인트**: 토큰 없이 통과하는 경로. 예전 gateway는 `"METHOD:path".startsWith(...)` 접두 비교라
 *   `/api/v1/auth/login-history` 같은 경로가 추가되면 자동으로 공개됐다. 여기서는 **메서드 + 경로 정확 일치**로 판정한다.
 *
 * 경로는 디코딩된 애플리케이션 내부 경로다. `;`, `%2F`, `//`, `..`가 든 요청은 그 전에 `StrictHttpFirewall`이 거절한다.
 */
object ApiPathPolicy {

    /** gateway 라우트와 같은 prefix. 정확히 일치하거나 `prefix/`로 시작해야 한다. */
    private val ROUTED_PREFIXES = listOf(
        "/api/v1/auth",
        "/api/v1/member",
        "/api/v1/admin",
        "/api/v1/stop",
        "/api/v1/reservation",
        "/api/v1/contract",
    )

    /** springdoc 경로. 로컬·문서 생성 프로필에서만 연다(`app.security.expose-api-docs`). */
    private val API_DOC_PREFIXES = listOf("/swagger-ui", "/api-docs")
    private val API_DOC_PATHS = setOf("/swagger-ui.html", "/api-docs.yaml")

    /**
     * 토큰 없이 통과하는 엔드포인트(정확 일치).
     *
     * 예전 gateway `PUBLIC_PATHS`에서 핸들러가 없는 `GET /api/v1/{auth,member,reservation,stop}/health-check` 4개는 뺐다.
     * 그중 reservation 것은 `GET /api/v1/reservation/{reservationId}`에 매칭돼, 공개 예외가 보호 핸들러에 걸려 있었다.
     * 헬스체크는 관리 포트의 `/actuator/health`를 쓴다.
     */
    private val PUBLIC_ENDPOINTS: Set<String> = setOf(
        "POST /api/v1/auth/register",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/token/refresh",
        "POST /api/v1/auth/admin/login",
        "POST /api/v1/auth/admin/token/refresh",
        "POST /api/v1/auth/admin/reactivate",
        // 로그인하지 못한 사용자가 쓰는 복구 흐름 — 요청 body 로만 본인을 확인한다.
        "POST /api/v1/auth/reactivate",
        "POST /api/v1/auth/password-reset/request",
        "POST /api/v1/auth/password-reset/verify",
        "POST /api/v1/auth/password-reset/confirm",
        "GET /api/v1/auth/verify-email",
        "GET /api/v1/member/find/me",
    )

    /** 관리자 사이트 토큰(`aud=ADMIN`)만 통과시키는 prefix. */
    private const val ADMIN_PREFIX = "/api/v1/admin"

    fun isRoutable(path: String, exposeApiDocs: Boolean): Boolean =
        ROUTED_PREFIXES.any { matchesPrefix(path, it) } || (exposeApiDocs && isApiDoc(path))

    fun isApiDoc(path: String): Boolean =
        path in API_DOC_PATHS || API_DOC_PREFIXES.any { matchesPrefix(path, it) }

    fun isPublic(method: String, path: String): Boolean = "$method $path" in PUBLIC_ENDPOINTS

    fun isAdmin(path: String): Boolean = matchesPrefix(path, ADMIN_PREFIX)

    private fun matchesPrefix(path: String, prefix: String): Boolean =
        path == prefix || path.startsWith("$prefix/")
}

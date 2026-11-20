package com.media.bus.app.security

/** 공개 포트(8080) 경로 정책 */
object ApiPathPolicy {

    private val ROUTED_PREFIXES = listOf(
        "/api/v1/auth",
        "/api/v1/member",
        "/api/v1/admin",
        "/api/v1/stop",
        "/api/v1/reservation",
        "/api/v1/contract",
    )

    private val API_DOC_PREFIXES = listOf("/swagger-ui", "/api-docs")
    private val API_DOC_PATHS = setOf("/swagger-ui.html", "/api-docs.yaml")

    private val PUBLIC_ENDPOINTS: Set<String> = setOf(
        "POST /api/v1/auth/register",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/token/refresh",
        "POST /api/v1/auth/admin/login",
        "POST /api/v1/auth/admin/token/refresh",
        "POST /api/v1/auth/admin/reactivate",
        "POST /api/v1/auth/reactivate",
        "POST /api/v1/auth/password-reset/request",
        "POST /api/v1/auth/password-reset/verify",
        "POST /api/v1/auth/password-reset/confirm",
        "GET /api/v1/auth/verify-email",
        "POST /api/v1/member/find/me",
    )

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

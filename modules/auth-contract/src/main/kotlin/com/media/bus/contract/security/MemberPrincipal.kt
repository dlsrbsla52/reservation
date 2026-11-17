package com.media.bus.contract.security

import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import io.jsonwebtoken.Claims
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.util.*

/**
 * ## JWT 클레임에서 복원되는 인증된 회원 정보 객체
 *
 * 생성 경로: `app`의 인증 필터가 검증한 액세스 토큰의 Claims에서 [fromClaims]로 복원한다.
 * 클라이언트가 보낸 `X-User-*` 헤더로는 만들지 않는다(모듈러 모놀리스 전환 후 헤더 신뢰 경로 제거).
 *
 * request attribute key([REQUEST_ATTRIBUTE_KEY])로 HttpServletRequest에 저장되어
 * AuthorizeHandlerInterceptor 및 CurrentMemberArgumentResolver에서 참조됩니다.
 *
 * 설계 의도: HttpServletRequest.setAttribute()는 요청 객체에 직접 저장이므로
 * ThreadLocal 없이 Virtual Thread 안전하게 사용할 수 있습니다.
 *
 * 권한(permissions)은 로그인 시 DB(auth.role_permission)에서 조회되어
 * JWT claim에서 바로 복원됩니다.
 * DB가 단일 source of truth이며, Access Token TTL(60분) 내 stale 허용.
 */
data class MemberPrincipal(
    val id: UUID,
    val loginId: String?,
    val email: String?,
    val memberType: MemberType,
    val emailVerified: Boolean,
    val permissions: Set<Permission>,
) {
    companion object {
        private val log = LoggerFactory.getLogger(MemberPrincipal::class.java)

        // ──────────────────────────────────────────────────────────────
        // 예전 gateway가 하위 서비스에 주입하던 신뢰 헤더 이름.
        // 지금은 아무도 읽지 않고, 인증 필터가 위조 방지를 위해 모든 요청에서 **제거하는 목록**으로만 쓴다.
        // ──────────────────────────────────────────────────────────────
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_LOGIN_ID = "X-User-Login-Id"
        const val HEADER_USER_EMAIL = "X-User-Email"
        const val HEADER_USER_ROLE = "X-User-Role"
        const val HEADER_EMAIL_VERIFIED = "X-Email-Verified"
        const val HEADER_USER_PERMISSIONS = "X-User-Permissions"

        /** 인증 필터가 모든 요청에서 제거하는 신뢰 헤더 6개. `X-Email-Verified`는 `X-User-` 접두가 아니므로 접두 검사로 대신하지 않는다. */
        val TRUSTED_HEADERS: Set<String> = setOf(
            HEADER_USER_ID,
            HEADER_USER_LOGIN_ID,
            HEADER_USER_EMAIL,
            HEADER_USER_ROLE,
            HEADER_EMAIL_VERIFIED,
            HEADER_USER_PERMISSIONS,
        )

        /** HttpServletRequest attribute 키 — Filter/Interceptor/Resolver가 공유합니다. */
        const val REQUEST_ATTRIBUTE_KEY = "authenticatedMember"

        // JWT 클레임 키 (JwtProvider와의 계약)
        const val CLAIM_LOGIN_ID = "loginId"
        const val CLAIM_EMAIL = "email"
        const val CLAIM_MEMBER_TYPE = "memberType"
        const val CLAIM_EMAIL_VERIFIED = "emailVerified"
        /** JWT claim 키 — 권한 목록. 쉼표 구분 예: "READ,WRITE" */
        const val CLAIM_PERMISSIONS = "permissions"

        // ──────────────────────────────────────────────────────────────
        // 정적 팩토리 메서드
        // ──────────────────────────────────────────────────────────────

        /**
         * HttpServletRequest의 Authorization 헤더에서 Bearer 토큰 문자열을 추출합니다.
         * Bearer 접두사가 없거나 헤더가 없으면 null을 반환합니다.
         *
         * @param request 현재 HTTP 요청
         * @return Bearer 접두사를 제거한 JWT 문자열, 없으면 null
         */
        fun extractBearerToken(request: HttpServletRequest): String? {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7)
            }
            return null
        }

        /**
         * JWT Claims에서 MemberPrincipal을 복원합니다.
         * JwtProvider 내부 전용 팩토리입니다.
         */
        fun fromClaims(claims: Claims): MemberPrincipal = MemberPrincipal(
            id = UUID.fromString(claims.subject),
            loginId = claims.get(CLAIM_LOGIN_ID, String::class.java),
            email = claims.get(CLAIM_EMAIL, String::class.java),
            memberType = MemberType.valueOf(claims.get(CLAIM_MEMBER_TYPE, String::class.java)),
            emailVerified = claims.get(CLAIM_EMAIL_VERIFIED, java.lang.Boolean::class.java)?.booleanValue() ?: false,
            permissions = parsePermissions(claims.get(CLAIM_PERMISSIONS, String::class.java)),
        )

        // ──────────────────────────────────────────────────────────────
        // 내부 유틸
        // ──────────────────────────────────────────────────────────────

        /**
         * "READ,WRITE" 형태의 문자열을 `Set<Permission>`으로 파싱합니다.
         * null 또는 빈 문자열이면 빈 Set을 반환합니다.
         * 알 수 없는 권한 이름은 warn 로그 후 무시합니다.
         */
        private fun parsePermissions(permissionsStr: String?): Set<Permission> {
            if (permissionsStr.isNullOrBlank()) {
                return Collections.emptySet()
            }
            val result = EnumSet.noneOf(Permission::class.java)
            for (name in permissionsStr.split(",")) {
                val trimmed = name.trim()
                try {
                    result.add(Permission.valueOf(trimmed))
                } catch (e: IllegalArgumentException) {
                    log.warn("[MemberPrincipal] 알 수 없는 권한 무시: {}", trimmed)
                }
            }
            return result
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 편의 위임 메서드
    // ──────────────────────────────────────────────────────────────

    /** ADMIN 카테고리 여부 */
    fun isAdmin(): Boolean = memberType.isAdmin

    /**
     * 해당 Permission 보유 여부.
     * DB에서 조회된 권한 Set을 직접 확인합니다.
     * MANAGE 권한 보유 시 모든 Permission 요청을 통과합니다.
     */
    fun hasPermission(permission: Permission): Boolean {
        if (permissions.contains(Permission.MANAGE)) return true
        return permissions.contains(permission)
    }

    /** 소속 카테고리 반환 */
    fun category(): MemberCategory = memberType.category
}

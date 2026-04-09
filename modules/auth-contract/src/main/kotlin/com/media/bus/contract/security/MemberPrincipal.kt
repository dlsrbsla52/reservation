package com.media.bus.contract.security

import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal.Companion.REQUEST_ATTRIBUTE_KEY
import com.media.bus.contract.security.MemberPrincipal.Companion.fromClaims
import com.media.bus.contract.security.MemberPrincipal.Companion.fromHeaders
import io.jsonwebtoken.Claims
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.util.*

/**
 * ## JWT 클레임 / X-User-* 헤더에서 복원되는 인증된 회원 정보 객체
 *
 * 생성 경로:
 * - Gateway -> 하위 서비스: MemberPrincipalExtractFilter가 X-User-* 헤더에서 [fromHeaders]로 복원
 * - auth 모듈 내부: JwtProvider가 Claims에서 [fromClaims]로 복원
 *
 * request attribute key([REQUEST_ATTRIBUTE_KEY])로 HttpServletRequest에 저장되어
 * AuthorizeHandlerInterceptor 및 CurrentMemberArgumentResolver에서 참조됩니다.
 *
 * 설계 의도: HttpServletRequest.setAttribute()는 요청 객체에 직접 저장이므로
 * ThreadLocal 없이 Virtual Thread 안전하게 사용할 수 있습니다.
 *
 * 권한(permissions)은 로그인 시 DB(auth.role_permission)에서 조회되어
 * JWT claim -> Gateway 헤더 -> 하위 서비스 복원 흐름으로 전달됩니다.
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
        // Gateway <-> 하위 서비스 간 헤더 계약 (중앙 관리)
        // JwtAuthenticationFilter와 MemberPrincipalExtractFilter가 동일 상수 참조
        // ──────────────────────────────────────────────────────────────
        @JvmField val HEADER_USER_ID = "X-User-Id"
        @JvmField val HEADER_USER_LOGIN_ID = "X-User-Login-Id"
        @JvmField val HEADER_USER_EMAIL = "X-User-Email"
        @JvmField val HEADER_USER_ROLE = "X-User-Role"
        @JvmField val HEADER_EMAIL_VERIFIED = "X-Email-Verified"
        /** Gateway -> 하위 서비스 권한 전달 헤더. 쉼표 구분 예: "READ,WRITE" */
        @JvmField val HEADER_USER_PERMISSIONS = "X-User-Permissions"

        /** HttpServletRequest attribute 키 — Filter/Interceptor/Resolver가 공유합니다. */
        @JvmField val REQUEST_ATTRIBUTE_KEY = "authenticatedMember"

        // JWT 클레임 키 (JwtProvider와의 계약)
        @JvmField val CLAIM_LOGIN_ID = "loginId"
        @JvmField val CLAIM_EMAIL = "email"
        @JvmField val CLAIM_MEMBER_TYPE = "memberType"
        @JvmField val CLAIM_EMAIL_VERIFIED = "emailVerified"
        /** JWT claim 키 — 권한 목록. 쉼표 구분 예: "READ,WRITE" */
        @JvmField val CLAIM_PERMISSIONS = "permissions"

        // ──────────────────────────────────────────────────────────────
        // 정적 팩토리 메서드
        // ──────────────────────────────────────────────────────────────

        /**
         * Gateway가 주입한 X-User-* 헤더에서 MemberPrincipal을 복원합니다.
         * MemberPrincipalExtractFilter 전용. "ROLE_" prefix 제거 후 MemberType을 파싱합니다.
         *
         * @param permissionsHeader "READ,WRITE" 형태의 권한 헤더 (null 허용 -> 빈 Set)
         * @throws IllegalArgumentException memberId 또는 role 파싱 실패 시
         */
        @JvmStatic
        fun fromHeaders(
            memberId: String,
            loginId: String?,
            email: String?,
            role: String?,
            emailVerified: String?,
            permissionsHeader: String?,
        ): MemberPrincipal {
            // Gateway가 "ROLE_ADMIN_USER" 형태로 주입하므로 prefix 제거
            val memberTypeName = if (role != null && role.startsWith("ROLE_")) {
                role.substring(5)
            } else {
                role
            }

            return MemberPrincipal(
                id = UUID.fromString(memberId),
                loginId = loginId,
                email = email,
                memberType = MemberType.valueOf(memberTypeName!!),
                emailVerified = emailVerified.toBoolean(),
                permissions = parsePermissions(permissionsHeader),
            )
        }

        /**
         * HttpServletRequest의 Authorization 헤더에서 Bearer 토큰 문자열을 추출합니다.
         * Bearer 접두사가 없거나 헤더가 없으면 null을 반환합니다.
         *
         * @param request 현재 HTTP 요청
         * @return Bearer 접두사를 제거한 JWT 문자열, 없으면 null
         */
        @JvmStatic
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
        @JvmStatic
        fun fromClaims(claims: Claims): MemberPrincipal = MemberPrincipal(
            id = UUID.fromString(claims.subject),
            loginId = claims.get(CLAIM_LOGIN_ID, String::class.java),
            email = claims.get(CLAIM_EMAIL, String::class.java),
            memberType = MemberType.valueOf(claims.get(CLAIM_MEMBER_TYPE, String::class.java)),
            emailVerified = java.lang.Boolean.TRUE == claims.get(CLAIM_EMAIL_VERIFIED, java.lang.Boolean::class.java),
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
        @JvmStatic
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

package com.media.bus.contract.security;

import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.entity.member.Permission;
import io.jsonwebtoken.Claims;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/// JWT 클레임 / X-User-\* 헤더에서 복원되는 인증된 회원 정보 객체.
/// 생성 경로:
/// - Gateway → 하위 서비스: MemberPrincipalExtractFilter가 X-User-\* 헤더에서 fromHeaders()로 복원
/// - auth 모듈 내부:         JwtProvider가 Claims에서 fromClaims()로 복원
/// request attribute key(REQUEST\_ATTRIBUTE\_KEY)로 HttpServletRequest에 저장되어
/// AuthorizeHandlerInterceptor 및 CurrentMemberArgumentResolver에서 참조됩니다.
/// 설계 의도: HttpServletRequest.setAttribute()는 요청 객체에 직접 저장이므로
/// ThreadLocal 없이 Virtual Thread 안전하게 사용할 수 있습니다.
/// 권한(permissions)은 로그인 시 DB(auth.role\_permission)에서 조회되어
/// JWT claim → Gateway 헤더 → 하위 서비스 복원 흐름으로 전달됩니다.
/// DB가 단일 source of truth이며, Access Token TTL(60분) 내 stale 허용.
@Slf4j
@Builder
public record MemberPrincipal(
    UUID id,
    String loginId,
    String email,
    MemberType memberType,
    boolean emailVerified,
    Set<Permission> permissions
) {

    // ──────────────────────────────────────────────────────────────
    // Gateway ↔ 하위 서비스 간 헤더 계약 (중앙 관리)
    // JwtAuthenticationFilter와 MemberPrincipalExtractFilter가 동일 상수 참조
    // ──────────────────────────────────────────────────────────────
    public static final String HEADER_USER_ID          = "X-User-Id";
    public static final String HEADER_USER_LOGIN_ID    = "X-User-Login-Id";
    public static final String HEADER_USER_EMAIL       = "X-User-Email";
    public static final String HEADER_USER_ROLE        = "X-User-Role";
    public static final String HEADER_EMAIL_VERIFIED   = "X-Email-Verified";
    /// Gateway → 하위 서비스 권한 전달 헤더. 쉼표 구분 예: "READ,WRITE"
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    /// HttpServletRequest attribute 키 — Filter/Interceptor/Resolver가 공유합니다.
    public static final String REQUEST_ATTRIBUTE_KEY = "authenticatedMember";

    // JWT 클레임 키 (JwtProvider와의 계약)
    public static final String CLAIM_LOGIN_ID       = "loginId";
    public static final String CLAIM_EMAIL          = "email";
    public static final String CLAIM_MEMBER_TYPE    = "memberType";
    public static final String CLAIM_EMAIL_VERIFIED = "emailVerified";
    /// JWT claim 키 — 권한 목록. 쉼표 구분 예: "READ,WRITE"
    public static final String CLAIM_PERMISSIONS    = "permissions";

    // ──────────────────────────────────────────────────────────────
    // Compact 생성자 — null permissions를 빈 Set으로 정규화
    // Lombok @Builder의 build()가 canonical constructor를 호출하므로 항상 실행됨
    // ──────────────────────────────────────────────────────────────
    public MemberPrincipal {
        permissions = (permissions == null)
            ? Collections.emptySet()
            : Collections.unmodifiableSet(permissions);
    }

    // ──────────────────────────────────────────────────────────────
    // 정적 팩토리 메서드
    // ──────────────────────────────────────────────────────────────

    /// Gateway가 주입한 X-User-\* 헤더에서 MemberPrincipal을 복원합니다.
    /// MemberPrincipalExtractFilter 전용. "ROLE\_" prefix 제거 후 MemberType을 파싱합니다.
    ///
    /// @param permissionsHeader "READ,WRITE" 형태의 권한 헤더 (null 허용 → 빈 Set)
    /// @throws IllegalArgumentException memberId 또는 role 파싱 실패 시
    public static MemberPrincipal fromHeaders(
        String memberId,
        String loginId,
        String email,
        String role,
        String emailVerified,
        String permissionsHeader
    ) {
        // Gateway가 "ROLE_ADMIN_USER" 형태로 주입하므로 prefix 제거
        String memberTypeName = (role != null && role.startsWith("ROLE_"))
            ? role.substring(5)
            : role;

        return new MemberPrincipal(
            UUID.fromString(memberId),
            loginId,
            email,
            MemberType.valueOf(memberTypeName),
            Boolean.parseBoolean(emailVerified),
            parsePermissions(permissionsHeader)
        );
    }

    /// JWT Claims에서 MemberPrincipal을 복원합니다.
    /// JwtProvider 내부 전용 팩토리입니다.
    public static MemberPrincipal fromClaims(Claims claims) {
        return new MemberPrincipal(
            UUID.fromString(claims.getSubject()),
            claims.get(CLAIM_LOGIN_ID, String.class),
            claims.get(CLAIM_EMAIL, String.class),
            MemberType.valueOf(claims.get(CLAIM_MEMBER_TYPE, String.class)),
            Boolean.TRUE.equals(claims.get(CLAIM_EMAIL_VERIFIED, Boolean.class)),
            parsePermissions(claims.get(CLAIM_PERMISSIONS, String.class))
        );
    }

    // ──────────────────────────────────────────────────────────────
    // 편의 위임 메서드
    // ──────────────────────────────────────────────────────────────

    /// ADMIN 카테고리 여부
    public boolean isAdmin() {
        return memberType.isAdmin();
    }

    /// 해당 Permission 보유 여부.
    /// DB에서 조회된 권한 Set을 직접 확인합니다.
    /// MANAGE 권한 보유 시 모든 Permission 요청을 통과합니다.
    public boolean hasPermission(Permission permission) {
        if (permissions.contains(Permission.MANAGE)) return true;
        return permissions.contains(permission);
    }

    /// 소속 카테고리 반환
    public MemberCategory category() {
        return memberType.getCategory();
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────

    /// "READ,WRITE" 형태의 문자열을 Set<Permission>으로 파싱합니다.
    /// null 또는 빈 문자열이면 빈 Set을 반환합니다.
    /// 알 수 없는 권한 이름은 warn 로그 후 무시합니다.
    private static Set<Permission> parsePermissions(String permissionsStr) {
        if (permissionsStr == null || permissionsStr.isBlank()) {
            return Collections.emptySet();
        }
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        for (String name : permissionsStr.split(",")) {
            String trimmed = name.trim();
            try {
                result.add(Permission.valueOf(trimmed));
            } catch (IllegalArgumentException e) {
                log.warn("[MemberPrincipal] 알 수 없는 권한 무시: {}", trimmed);
            }
        }
        return result;
    }
}

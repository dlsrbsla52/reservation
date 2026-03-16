package com.media.bus.contract.security;

import com.media.bus.contract.entity.member.MemberType;
import lombok.Builder;

/**
 * JWT 클레임에 담기는 인증된 회원 정보 객체.
 * Gateway에서 토큰을 검증한 후 이 객체의 필드들을 X-User-* 헤더로 하위 서비스에 전달합니다.
 */
@Builder
public record MemberPrincipal(
    String id, // UUID (PK)
    String loginId,
    String email,
    MemberType memberType,
    boolean emailVerified
) {
    /**
     * JWT 클레임에서 사용할 키 상수 정의.
     * 클레임 이름을 중앙 관리하여 Gateway Filter / JwtProvider 간 불일치를 방지합니다.
     */
    public static final String CLAIM_LOGIN_ID = "loginId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_MEMBER_TYPE = "memberType";
    public static final String CLAIM_EMAIL_VERIFIED = "emailVerified";
}

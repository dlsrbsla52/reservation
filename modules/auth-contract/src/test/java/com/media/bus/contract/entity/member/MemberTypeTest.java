package com.media.bus.contract.entity.member;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTypeTest {

    // ──────────────────────────────────────────────────────────────
    // 카테고리 분류 테스트 (hasPermission은 MemberType에서 제거됨 — DB 기반으로 변경)
    // ──────────────────────────────────────────────────────────────

    @Test
    void MEMBER_isUser() {
        assertThat(MemberType.MEMBER.isUser()).isTrue();
        assertThat(MemberType.MEMBER.isAdmin()).isFalse();
        assertThat(MemberType.MEMBER.isBusiness()).isFalse();
    }

    @Test
    void BUSINESS_isBusiness() {
        assertThat(MemberType.BUSINESS.isBusiness()).isTrue();
        assertThat(MemberType.BUSINESS.isAdmin()).isFalse();
        assertThat(MemberType.BUSINESS.isUser()).isFalse();
    }

    @Test
    void ADMIN_USER_isAdmin() {
        assertThat(MemberType.ADMIN_USER.isAdmin()).isTrue();
        assertThat(MemberType.ADMIN_USER.isUser()).isFalse();
        assertThat(MemberType.ADMIN_USER.isBusiness()).isFalse();
    }

    @Test
    void ADMIN_MASTER_카테고리_ADMIN() {
        assertThat(MemberType.ADMIN_MASTER.getCategory()).isEqualTo(MemberCategory.ADMIN);
        assertThat(MemberType.ADMIN_MASTER.isAdmin()).isTrue();
    }

    @Test
    void ADMIN_DEVELOPER_카테고리_ADMIN() {
        assertThat(MemberType.ADMIN_DEVELOPER.getCategory()).isEqualTo(MemberCategory.ADMIN);
        assertThat(MemberType.ADMIN_DEVELOPER.isAdmin()).isTrue();
    }
}

package com.media.bus.contract.entity.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemberTypeTest {

    // 카테고리 분류 테스트 (hasPermission은 MemberType에서 제거됨 — DB 기반으로 변경)

    @Test
    fun `MEMBER isUser`() {
        assertThat(MemberType.MEMBER.isUser).isTrue()
        assertThat(MemberType.MEMBER.isAdmin).isFalse()
        assertThat(MemberType.MEMBER.isBusiness).isFalse()
    }

    @Test
    fun `BUSINESS isBusiness`() {
        assertThat(MemberType.BUSINESS.isBusiness).isTrue()
        assertThat(MemberType.BUSINESS.isAdmin).isFalse()
        assertThat(MemberType.BUSINESS.isUser).isFalse()
    }

    @Test
    fun `ADMIN_USER isAdmin`() {
        assertThat(MemberType.ADMIN_USER.isAdmin).isTrue()
        assertThat(MemberType.ADMIN_USER.isUser).isFalse()
        assertThat(MemberType.ADMIN_USER.isBusiness).isFalse()
    }

    @Test
    fun `ADMIN_MASTER 카테고리 ADMIN`() {
        assertThat(MemberType.ADMIN_MASTER.category).isEqualTo(MemberCategory.ADMIN)
        assertThat(MemberType.ADMIN_MASTER.isAdmin).isTrue()
    }

    @Test
    fun `ADMIN_DEVELOPER 카테고리 ADMIN`() {
        assertThat(MemberType.ADMIN_DEVELOPER.category).isEqualTo(MemberCategory.ADMIN)
        assertThat(MemberType.ADMIN_DEVELOPER.isAdmin).isTrue()
    }
}

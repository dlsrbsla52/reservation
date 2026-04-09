package com.media.bus.contract.security.filter

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class MemberPrincipalExtractFilterTest {

    private lateinit var filter: MemberPrincipalExtractFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setUp() {
        filter = MemberPrincipalExtractFilter()
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        chain = mockk(relaxed = true)
    }

    @Test
    fun `헤더 정상 주입시 principal attribute 저장`() {
        val memberId = "550e8400-e29b-41d4-a716-446655440000"
        request.addHeader(MemberPrincipal.HEADER_USER_ID, memberId)
        request.addHeader(MemberPrincipal.HEADER_USER_LOGIN_ID, "admin")
        request.addHeader(MemberPrincipal.HEADER_USER_EMAIL, "admin@test.com")
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_MASTER")
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true")

        filter.doFilter(request, response, chain)

        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as MemberPrincipal
        assertThat(principal).isNotNull()
        assertThat(principal.memberType).isEqualTo(MemberType.ADMIN_MASTER)
        assertThat(principal.loginId).isEqualTo("admin")
        assertThat(principal.emailVerified).isTrue()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `permissions 헤더 파싱 후 principal에 포함`() {
        val memberId = "550e8400-e29b-41d4-a716-446655440000"
        request.addHeader(MemberPrincipal.HEADER_USER_ID, memberId)
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_USER")
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true")
        request.addHeader(MemberPrincipal.HEADER_USER_PERMISSIONS, "READ,WRITE")

        filter.doFilter(request, response, chain)

        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as MemberPrincipal
        assertThat(principal).isNotNull()
        assertThat(principal.permissions).containsExactlyInAnyOrder(Permission.READ, Permission.WRITE)
        assertThat(principal.hasPermission(Permission.READ)).isTrue()
        assertThat(principal.hasPermission(Permission.WRITE)).isTrue()
        assertThat(principal.hasPermission(Permission.DELETE)).isFalse()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `permissions 헤더 없으면 빈 Set으로 복원`() {
        val memberId = "550e8400-e29b-41d4-a716-446655440000"
        request.addHeader(MemberPrincipal.HEADER_USER_ID, memberId)
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER")
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "false")

        filter.doFilter(request, response, chain)

        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as MemberPrincipal
        assertThat(principal).isNotNull()
        assertThat(principal.permissions).isEmpty()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `MANAGE 권한 보유시 모든 permission 통과`() {
        val memberId = "550e8400-e29b-41d4-a716-446655440000"
        request.addHeader(MemberPrincipal.HEADER_USER_ID, memberId)
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_MASTER")
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true")
        request.addHeader(MemberPrincipal.HEADER_USER_PERMISSIONS, "READ,WRITE,DELETE,MANAGE")

        filter.doFilter(request, response, chain)

        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as MemberPrincipal
        assertThat(principal).isNotNull()
        assertThat(principal.hasPermission(Permission.READ)).isTrue()
        assertThat(principal.hasPermission(Permission.WRITE)).isTrue()
        assertThat(principal.hasPermission(Permission.DELETE)).isTrue()
        assertThat(principal.hasPermission(Permission.MANAGE)).isTrue()
    }

    @Test
    fun `필수 헤더 누락시 attribute 미설정 체인 통과`() {
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER")

        filter.doFilter(request, response, chain)

        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `role 헤더 누락시 attribute 미설정 체인 통과`() {
        request.addHeader(MemberPrincipal.HEADER_USER_ID, "550e8400-e29b-41d4-a716-446655440000")

        filter.doFilter(request, response, chain)

        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `잘못된 UUID 파싱 실패시 attribute 미설정 체인 통과`() {
        request.addHeader(MemberPrincipal.HEADER_USER_ID, "not-a-uuid")
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER")

        filter.doFilter(request, response, chain)

        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull()
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `ROLE prefix 없는 role 헤더도 정상 파싱`() {
        val memberId = "550e8400-e29b-41d4-a716-446655440000"
        request.addHeader(MemberPrincipal.HEADER_USER_ID, memberId)
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "MEMBER")
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "false")

        filter.doFilter(request, response, chain)

        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as MemberPrincipal
        assertThat(principal).isNotNull()
        assertThat(principal.memberType).isEqualTo(MemberType.MEMBER)
        assertThat(principal.emailVerified).isFalse()
    }
}

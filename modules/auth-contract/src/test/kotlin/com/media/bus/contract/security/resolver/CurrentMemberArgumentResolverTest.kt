package com.media.bus.contract.security.resolver

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.CurrentMember
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.ServletWebRequest
import java.util.*

class CurrentMemberArgumentResolverTest {

    private lateinit var resolver: CurrentMemberArgumentResolver
    private lateinit var request: MockHttpServletRequest
    private lateinit var webRequest: NativeWebRequest

    @BeforeEach
    fun setUp() {
        resolver = CurrentMemberArgumentResolver()
        request = MockHttpServletRequest()
        webRequest = ServletWebRequest(request)
    }

    @Test
    fun `CurrentMember 파라미터 지원`() {
        val param = param("required")
        assertThat(resolver.supportsParameter(param)).isTrue()
    }

    @Test
    fun `required true principal 있으면 반환`() {
        val principal = MemberPrincipal(
            UUID.randomUUID(), "user", "user@test.com", MemberType.ADMIN_USER, true, emptySet()
        )
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal)

        val result = resolver.resolveArgument(param("required"), null, webRequest, null)
        assertThat(result).isEqualTo(principal)
    }

    @Test
    fun `required true principal 없으면 401`() {
        assertThatThrownBy { resolver.resolveArgument(param("required"), null, webRequest, null) }
            .isInstanceOf(NoAuthenticationException::class.java)
    }

    @Test
    fun `required false principal 없으면 null 반환`() {
        val result = resolver.resolveArgument(param("optional"), null, webRequest, null)
        assertThat(result).isNull()
    }

    @Test
    fun `required false principal 있으면 반환`() {
        val principal = MemberPrincipal(
            UUID.randomUUID(), "user", "user@test.com", MemberType.MEMBER, false, emptySet()
        )
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal)

        val result = resolver.resolveArgument(param("optional"), null, webRequest, null)
        assertThat(result).isEqualTo(principal)
    }

    private fun param(methodName: String): MethodParameter {
        val method = DummyController::class.java.getMethod(methodName, MemberPrincipal::class.java)
        return MethodParameter(method, 0)
    }

    @Suppress("unused")
    class DummyController {
        fun required(@CurrentMember p: MemberPrincipal) {}
        fun optional(@CurrentMember(required = false) p: MemberPrincipal) {}
    }
}

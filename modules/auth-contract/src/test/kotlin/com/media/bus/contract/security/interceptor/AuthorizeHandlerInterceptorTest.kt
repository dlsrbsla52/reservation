package com.media.bus.contract.security.interceptor

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import java.util.*

class AuthorizeHandlerInterceptorTest {

    private lateinit var interceptor: AuthorizeHandlerInterceptor
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        interceptor = AuthorizeHandlerInterceptor()
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    @Test
    fun `Authorize 어노테이션 없으면 통과`() {
        val handler = handlerMethod("noAnnotation")
        val result = interceptor.preHandle(request, response, handler)
        assertThat(result).isTrue()
    }

    @Test
    fun `principal 없으면 401`() {
        val handler = handlerMethod("adminOnly")
        assertThatThrownBy { interceptor.preHandle(request, response, handler) }
            .isInstanceOf(NoAuthenticationException::class.java)
    }

    @Test
    fun `MEMBER 카테고리 불일치 403`() {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.MEMBER))
        val handler = handlerMethod("adminOnly")
        assertThatThrownBy { interceptor.preHandle(request, response, handler) }
            .isInstanceOf(NoAuthorizationException::class.java)
    }

    @Test
    fun `ADMIN_USER 카테고리 매칭 통과`() {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.ADMIN_USER))
        val handler = handlerMethod("adminOnly")
        val result = interceptor.preHandle(request, response, handler)
        assertThat(result).isTrue()
    }

    @Test
    fun `MEMBER WRITE 권한 없어서 403`() {
        request.setAttribute(
            MemberPrincipal.REQUEST_ATTRIBUTE_KEY,
            principal(MemberType.MEMBER, setOf(Permission.READ))
        )
        val handler = handlerMethod("requireWrite")
        assertThatThrownBy { interceptor.preHandle(request, response, handler) }
            .isInstanceOf(NoAuthorizationException::class.java)
    }

    @Test
    fun `ADMIN_MASTER WRITE 권한 통과`() {
        request.setAttribute(
            MemberPrincipal.REQUEST_ATTRIBUTE_KEY,
            principal(
                MemberType.ADMIN_MASTER,
                setOf(Permission.READ, Permission.WRITE, Permission.DELETE, Permission.MANAGE)
            )
        )
        val handler = handlerMethod("requireWrite")
        val result = interceptor.preHandle(request, response, handler)
        assertThat(result).isTrue()
    }

    @Test
    fun `이메일 인증 완료 통과`() {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.ADMIN_MASTER))
        val handler = handlerMethod("requireEmailVerified")
        val result = interceptor.preHandle(request, response, handler)
        assertThat(result).isTrue()
    }

    @Test
    fun `categories types 모두 비어있으면 인증만으로 통과`() {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.MEMBER))
        val handler = handlerMethod("authenticatedOnly")
        val result = interceptor.preHandle(request, response, handler)
        assertThat(result).isTrue()
    }

    private fun handlerMethod(methodName: String): HandlerMethod {
        val controller = DummyController()
        val method = DummyController::class.java.getMethod(methodName)
        return HandlerMethod(controller, method)
    }

    private fun principal(type: MemberType, permissions: Set<Permission> = emptySet()) =
        MemberPrincipal(UUID.randomUUID(), "user", "user@test.com", type, true, permissions)

    @Suppress("unused")
    class DummyController {
        fun noAnnotation() {}

        @Authorize(categories = [MemberCategory.ADMIN])
        fun adminOnly() {}

        @Authorize(permissions = [Permission.WRITE])
        fun requireWrite() {}

        @Authorize(requireEmailVerified = true)
        fun requireEmailVerified() {}

        @Authorize
        fun authenticatedOnly() {}
    }
}

package com.media.bus.iam.admin.controller

import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.contract.security.interceptor.AuthorizeHandlerInterceptor
import com.media.bus.iam.admin.facade.AdminStopPriceFacade
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import java.util.*

class AdminStopRegistrationAuthorizationTest {
    private val controller = AdminStopPriceController(mockk<AdminStopPriceFacade>())
    private val interceptor = AuthorizeHandlerInterceptor()
    private fun authorize(method: String, audience: TokenAudience, permissions: Set<Permission>): Boolean {
        val request = MockHttpServletRequest().apply {
            setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, MemberPrincipal(
                UUID.randomUUID(), "dummy-admin", null, MemberType.ADMIN_USER, true, permissions, audience,
            ))
        }
        val handler = HandlerMethod(controller, AdminStopPriceController::class.java.methods.single { it.name == method })
        return interceptor.preHandle(request, MockHttpServletResponse(), handler)
    }

    @Test
    fun `관리자 사이트 ADMIN_USER의 WRITE는 단건 등록만 허용한다`() {
        assertThat(authorize("createSimpleStop", TokenAudience.ADMIN, setOf(Permission.WRITE))).isTrue()
        assertThrows<NoAuthorizationException> { authorize("createSimpleStop", TokenAudience.ADMIN, setOf(Permission.READ)) }
        assertThrows<NoAuthorizationException> { authorize("createSimpleStop", TokenAudience.USER, setOf(Permission.WRITE)) }
        assertThrows<NoAuthorizationException> { authorize("registerBulk", TokenAudience.ADMIN, setOf(Permission.WRITE)) }
    }
}

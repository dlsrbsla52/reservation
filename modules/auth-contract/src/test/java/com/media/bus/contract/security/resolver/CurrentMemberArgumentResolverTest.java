package com.media.bus.contract.security.resolver;

import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.CurrentMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentMemberArgumentResolverTest {

    CurrentMemberArgumentResolver resolver;
    MockHttpServletRequest request;
    NativeWebRequest webRequest;

    @BeforeEach
    void setUp() {
        resolver = new CurrentMemberArgumentResolver();
        request = new MockHttpServletRequest();
        webRequest = new ServletWebRequest(request);
    }

    @Test
    void CurrentMember_파라미터_지원() throws Exception {
        MethodParameter param = param("required");
        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    void required_true_principal_있으면_반환() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                UUID.randomUUID(), "user", "user@test.com", MemberType.ADMIN_USER, true, null
        );
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal);

        Object result = resolver.resolveArgument(param("required"), null, webRequest, null);
        assertThat(result).isEqualTo(principal);
    }

    @Test
    void required_true_principal_없으면_401() {
        assertThatThrownBy(() -> resolver.resolveArgument(param("required"), null, webRequest, null))
                .isInstanceOf(NoAuthenticationException.class);
    }

    @Test
    void required_false_principal_없으면_null_반환() throws Exception {
        Object result = resolver.resolveArgument(param("optional"), null, webRequest, null);
        assertThat(result).isNull();
    }

    @Test
    void required_false_principal_있으면_반환() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                UUID.randomUUID(), "user", "user@test.com", MemberType.MEMBER, false, null
        );
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal);

        Object result = resolver.resolveArgument(param("optional"), null, webRequest, null);
        assertThat(result).isEqualTo(principal);
    }

    private MethodParameter param(String methodName) throws NoSuchMethodException {
        Method method = DummyController.class.getMethod(methodName, MemberPrincipal.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    static class DummyController {
        public void required(@CurrentMember MemberPrincipal p) {}
        public void optional(@CurrentMember(required = false) MemberPrincipal p) {}
    }
}

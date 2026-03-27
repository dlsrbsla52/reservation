package com.media.bus.contract.security.interceptor;

import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.exceptions.NoAuthorizationException;
import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.Authorize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizeHandlerInterceptorTest {

    AuthorizeHandlerInterceptor interceptor;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AuthorizeHandlerInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void Authorize_어노테이션_없으면_통과() throws Exception {
        HandlerMethod handler = handlerMethod("noAnnotation");
        boolean result = interceptor.preHandle(request, response, handler);
        assertThat(result).isTrue();
    }

    @Test
    void principal_없으면_401() {
        HandlerMethod handler = handlerMethod("adminOnly");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(NoAuthenticationException.class);
    }

    @Test
    void MEMBER_카테고리_불일치_403() {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.MEMBER));
        HandlerMethod handler = handlerMethod("adminOnly");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(NoAuthorizationException.class);
    }

    @Test
    void ADMIN_USER_카테고리_매칭_통과() throws Exception {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.ADMIN_USER));
        HandlerMethod handler = handlerMethod("adminOnly");
        boolean result = interceptor.preHandle(request, response, handler);
        assertThat(result).isTrue();
    }

    @Test
    void MEMBER_WRITE_권한_없어서_403() {
        // MEMBER는 READ만 보유, WRITE 없음 → 403
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY,
                principal(MemberType.MEMBER, Set.of(Permission.READ)));
        HandlerMethod handler = handlerMethod("requireWrite");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(NoAuthorizationException.class);
    }

    @Test
    void ADMIN_MASTER_WRITE_권한_통과() throws Exception {
        // ADMIN_MASTER는 MANAGE 포함 — MANAGE 보유 시 모든 permission 통과
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY,
                principal(MemberType.ADMIN_MASTER,
                        EnumSet.of(Permission.READ, Permission.WRITE, Permission.DELETE, Permission.MANAGE)));
        HandlerMethod handler = handlerMethod("requireWrite");
        boolean result = interceptor.preHandle(request, response, handler);
        assertThat(result).isTrue();
    }

//    TODO : 추후 이메일 인증 활성화 시 테스트 코드 활성화 필요
//    @Test
//    void 이메일_미인증_403() {
//        MemberPrincipal unverified = new MemberPrincipal(
//                UUID.randomUUID(), "member", "user@test.com", MemberType.ADMIN_MASTER, false, null
//        );
//        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, unverified);
//        HandlerMethod handler = handlerMethod("requireEmailVerified");
//        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
//                .isInstanceOf(NoAuthorizationException.class);
//    }

    @Test
    void 이메일_인증_완료_통과() throws Exception {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.ADMIN_MASTER));
        HandlerMethod handler = handlerMethod("requireEmailVerified");
        boolean result = interceptor.preHandle(request, response, handler);
        assertThat(result).isTrue();
    }

    @Test
    void categories_types_모두_비어있으면_인증만으로_통과() throws Exception {
        request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal(MemberType.MEMBER));
        HandlerMethod handler = handlerMethod("authenticatedOnly");
        boolean result = interceptor.preHandle(request, response, handler);
        assertThat(result).isTrue();
    }

    // ──────────────────────────────────────────────────────────────
    // 테스트용 더미 핸들러 메서드 헬퍼
    // ──────────────────────────────────────────────────────────────

    private HandlerMethod handlerMethod(String methodName) {
        try {
            DummyController controller = new DummyController();
            Method method = DummyController.class.getMethod(methodName);
            return new HandlerMethod(controller, method);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /** 기본 권한 없는 principal 생성 — 카테고리·이메일 검증 테스트용 */
    private MemberPrincipal principal(MemberType type) {
        return new MemberPrincipal(UUID.randomUUID(), "user", "user@test.com", type, true, null);
    }

    /** 명시적 permissions를 가진 principal 생성 — 권한 검증 테스트용 */
    private MemberPrincipal principal(MemberType type, Set<Permission> permissions) {
        return new MemberPrincipal(UUID.randomUUID(), "user", "user@test.com", type, true, permissions);
    }

    @SuppressWarnings("unused")
    static class DummyController {

        public void noAnnotation() {}

        @Authorize(categories = {MemberCategory.ADMIN})
        public void adminOnly() {}

        @Authorize(permissions = {Permission.WRITE})
        public void requireWrite() {}

        @Authorize(requireEmailVerified = true)
        public void requireEmailVerified() {}

        @Authorize
        public void authenticatedOnly() {}
    }
}

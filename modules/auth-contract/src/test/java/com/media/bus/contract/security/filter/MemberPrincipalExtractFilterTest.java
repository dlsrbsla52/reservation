package com.media.bus.contract.security.filter;

import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.MemberPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberPrincipalExtractFilterTest {

    MemberPrincipalExtractFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new MemberPrincipalExtractFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void 헤더_정상_주입시_principal_attribute_저장() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(MemberPrincipal.HEADER_USER_ID, userId);
        request.addHeader(MemberPrincipal.HEADER_USER_LOGIN_ID, "admin");
        request.addHeader(MemberPrincipal.HEADER_USER_EMAIL, "admin@test.com");
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_MASTER");
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true");

        filter.doFilterInternal(request, response, chain);

        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);
        assertThat(principal).isNotNull();
        assertThat(principal.memberType()).isEqualTo(MemberType.ADMIN_MASTER);
        assertThat(principal.loginId()).isEqualTo("admin");
        assertThat(principal.emailVerified()).isTrue();
        verify(chain).doFilter(request, response);
    }

    @Test
    void permissions_헤더_파싱_후_principal에_포함() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(MemberPrincipal.HEADER_USER_ID, userId);
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_USER");
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true");
        request.addHeader(MemberPrincipal.HEADER_USER_PERMISSIONS, "READ,WRITE");

        filter.doFilterInternal(request, response, chain);

        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);
        assertThat(principal).isNotNull();
        assertThat(principal.permissions()).containsExactlyInAnyOrder(Permission.READ, Permission.WRITE);
        assertThat(principal.hasPermission(Permission.READ)).isTrue();
        assertThat(principal.hasPermission(Permission.WRITE)).isTrue();
        assertThat(principal.hasPermission(Permission.DELETE)).isFalse();
        verify(chain).doFilter(request, response);
    }

    @Test
    void permissions_헤더_없으면_빈_Set으로_복원() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(MemberPrincipal.HEADER_USER_ID, userId);
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER");
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "false");
        // X-User-Permissions 헤더 없음

        filter.doFilterInternal(request, response, chain);

        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);
        assertThat(principal).isNotNull();
        assertThat(principal.permissions()).isEmpty();
        verify(chain).doFilter(request, response);
    }

    @Test
    void MANAGE_권한_보유시_모든_permission_통과() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(MemberPrincipal.HEADER_USER_ID, userId);
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_MASTER");
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "true");
        request.addHeader(MemberPrincipal.HEADER_USER_PERMISSIONS, "READ,WRITE,DELETE,MANAGE");

        filter.doFilterInternal(request, response, chain);

        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);
        assertThat(principal).isNotNull();
        assertThat(principal.hasPermission(Permission.READ)).isTrue();
        assertThat(principal.hasPermission(Permission.WRITE)).isTrue();
        assertThat(principal.hasPermission(Permission.DELETE)).isTrue();
        assertThat(principal.hasPermission(Permission.MANAGE)).isTrue();
    }

    @Test
    void 필수_헤더_누락시_attribute_미설정_체인_통과() throws Exception {
        // X-User-Id 없음
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER");

        filter.doFilterInternal(request, response, chain);

        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void role_헤더_누락시_attribute_미설정_체인_통과() throws Exception {
        request.addHeader(MemberPrincipal.HEADER_USER_ID, "550e8400-e29b-41d4-a716-446655440000");
        // X-User-Role 없음

        filter.doFilterInternal(request, response, chain);

        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void 잘못된_UUID_파싱_실패시_attribute_미설정_체인_통과() throws Exception {
        request.addHeader(MemberPrincipal.HEADER_USER_ID, "not-a-uuid");
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "ROLE_MEMBER");

        filter.doFilterInternal(request, response, chain);

        // 파싱 실패 → warn 로그 후 attribute 미설정
        assertThat(request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void ROLE_prefix_없는_role_헤더도_정상_파싱() throws Exception {
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(MemberPrincipal.HEADER_USER_ID, userId);
        request.addHeader(MemberPrincipal.HEADER_USER_ROLE, "MEMBER"); // prefix 없음
        request.addHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED, "false");

        filter.doFilterInternal(request, response, chain);

        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);
        assertThat(principal).isNotNull();
        assertThat(principal.memberType()).isEqualTo(MemberType.MEMBER);
        assertThat(principal.emailVerified()).isFalse();
    }
}

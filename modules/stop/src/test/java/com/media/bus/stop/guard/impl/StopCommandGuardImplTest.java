package com.media.bus.stop.guard.impl;

import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.repository.StopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StopCommandGuardImplTest {

    @Mock
    JwtProvider jwtProvider;

    @Mock
    StopRepository stopRepository;

    @InjectMocks
    StopCommandGuardImpl stopCommandGuard;

    @Test
    void isMemberAuthenticationAdmin_ADMIN_USER_principal_반환() {
        MemberPrincipal principal = principal(MemberType.ADMIN_USER);
        when(jwtProvider.getMemberPrincipalFromRefreshToken("token")).thenReturn(principal);

        MemberPrincipal result = stopCommandGuard.isMemberAuthenticationAdmin("token");

        assertThat(result).isEqualTo(principal);
    }

    @Test
    void isMemberAuthenticationAdmin_ADMIN_MASTER_principal_반환() {
        MemberPrincipal principal = principal(MemberType.ADMIN_MASTER);
        when(jwtProvider.getMemberPrincipalFromRefreshToken("token")).thenReturn(principal);

        MemberPrincipal result = stopCommandGuard.isMemberAuthenticationAdmin("token");

        assertThat(result).isEqualTo(principal);
    }

    @Test
    void isMemberAuthenticationAdmin_MEMBER_예외_발생() {
        when(jwtProvider.getMemberPrincipalFromRefreshToken("token"))
                .thenReturn(principal(MemberType.MEMBER));

        assertThatThrownBy(() -> stopCommandGuard.isMemberAuthenticationAdmin("token"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void isMemberAuthenticationAdmin_BUSINESS_예외_발생() {
        when(jwtProvider.getMemberPrincipalFromRefreshToken("token"))
                .thenReturn(principal(MemberType.BUSINESS));

        assertThatThrownBy(() -> stopCommandGuard.isMemberAuthenticationAdmin("token"))
                .isInstanceOf(ServiceException.class);
    }

    private MemberPrincipal principal(MemberType type) {
        return MemberPrincipal.builder()
                .id(UUID.randomUUID())
                .loginId("user")
                .email("user@test.com")
                .memberType(type)
                .emailVerified(true)
                .build();
    }
}
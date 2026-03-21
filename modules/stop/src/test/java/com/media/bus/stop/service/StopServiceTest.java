package com.media.bus.stop.service;

import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.entity.enums.StopType;
import com.media.bus.stop.guard.StopCommandGuard;
import com.media.bus.stop.repository.StopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StopServiceTest {

    @Mock
    StopRepository stopRepository;

    @Mock
    StopCommandGuard stopCommandGuard;

    @InjectMocks
    StopService stopService;

    @Test
    void createOneStop_등록자_UUID와_USER_소스가_저장된다() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = MemberPrincipal.builder()
                .id(memberId)
                .loginId("admin")
                .email("admin@test.com")
                .memberType(MemberType.ADMIN_USER)
                .emailVerified(true)
                .build();
        SimpleStopCreateRequest request = new SimpleStopCreateRequest(
                "123", "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE
        );
        when(stopCommandGuard.isMemberAuthenticationAdmin("token")).thenReturn(principal);

        stopService.createOneStop("token", request);

        ArgumentCaptor<Stop> captor = ArgumentCaptor.forClass(Stop.class);
        verify(stopRepository).save(captor.capture());
        Stop saved = captor.getValue();

        assertThat(saved.getRegisteredById()).isEqualTo(memberId);
        assertThat(saved.getRegisteredBySource()).isEqualTo(ChangeSource.USER);
        assertThat(saved.getStopId()).isEqualTo("123");
    }

    @Test
    void createOneStop_권한_검증_후_정류소_등록_확인() {
        UUID memberId = UUID.randomUUID();
        MemberPrincipal principal = MemberPrincipal.builder()
                .id(memberId)
                .loginId("admin")
                .email("admin@test.com")
                .memberType(MemberType.ADMIN_MASTER)
                .emailVerified(true)
                .build();
        SimpleStopCreateRequest request = new SimpleStopCreateRequest(
                "999", "마스터정류소", "128.0", "36.0", "NODE999", StopType.VILLAGE_BUS
        );
        when(stopCommandGuard.isMemberAuthenticationAdmin("token")).thenReturn(principal);

        stopService.createOneStop("token", request);

        verify(stopCommandGuard).isMemberAuthenticationAdmin("token");
        verify(stopCommandGuard).isStopRegistered("999");
        verify(stopRepository).save(org.mockito.ArgumentMatchers.any(Stop.class));
    }
}
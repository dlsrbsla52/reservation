package com.media.bus.stop.guard.impl;

import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.stop.repository.StopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * StopCommandGuardImpl 단위 테스트.
 * JwtProvider 의존성은 인가 인터셉터로 이전되어 제거되었습니다.
 * Repository 기반 비즈니스 규칙 검증만 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
class StopCommandGuardImplTest {

    @Mock
    StopRepository stopRepository;

    @InjectMocks
    StopCommandGuardImpl stopCommandGuard;

    @Test
    void isStopRegistered_등록된_정류소_예외없음() {
        when(stopRepository.findByStopId("123")).thenReturn(Optional.of(new com.media.bus.stop.entity.Stop()));
        assertThatCode(() -> stopCommandGuard.isStopRegistered("123")).doesNotThrowAnyException();
    }

    @Test
    void isStopRegistered_미등록_정류소_ServiceException_발생() {
        when(stopRepository.findByStopId("999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stopCommandGuard.isStopRegistered("999"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("등록된 정류장을 찾을 수 없습니다.");
    }
}

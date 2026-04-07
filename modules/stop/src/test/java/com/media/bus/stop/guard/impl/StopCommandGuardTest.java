package com.media.bus.stop.guard.impl;

import com.media.bus.common.exceptions.BusinessException;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.guard.StopCommandGuard;
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

/// StopCommandGuard 단위 테스트.
/// H-01 수정 후 validateNotDuplicate() 동작 검증:
/// - stopId가 이미 존재하면 → 409 CONFLICT (중복 방지)
/// - stopId가 존재하지 않으면 → 예외 없음 (신규 등록 허용)
@ExtendWith(MockitoExtension.class)
class StopCommandGuardTest {

    @Mock
    StopRepository stopRepository;

    @InjectMocks
    StopCommandGuard stopCommandGuard;

    @Test
    void validateNotDuplicate_미등록_정류소_예외없음() {
        when(stopRepository.findByStopId("999")).thenReturn(Optional.empty());
        assertThatCode(() -> stopCommandGuard.validateNotDuplicate("999")).doesNotThrowAnyException();
    }

    @Test
    void validateNotDuplicate_이미등록된_정류소_BusinessException_발생() {
        when(stopRepository.findByStopId("123")).thenReturn(Optional.of(new Stop()));
        assertThatThrownBy(() -> stopCommandGuard.validateNotDuplicate("123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 등록된 정류장입니다.");
    }
}
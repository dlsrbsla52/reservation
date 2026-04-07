package com.media.bus.stop.service;

import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.entity.StopUpdateHistory;
import com.media.bus.stop.repository.StopRepository;
import com.media.bus.stop.repository.StopUpdateHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/// 페이지 단위 정류소 배치 저장 전담 서비스.
/// REQUIRES_NEW 전파 속성으로 독립 트랜잭션을 생성하여 페이지별 커밋을 보장한다.
/// 중간 실패 시 해당 페이지만 롤백되며, 이전 페이지의 커밋은 유지된다.
///
/// **주의:** self-invocation AOP 우회를 방지하기 위해 StopRegisterService에서 분리된 별도 빈이다.
/// StopRegisterService가 자신의 메서드를 직접 호출하면 @Transactional이 적용되지 않는다.
@Service
@RequiredArgsConstructor
public class StopBulkPersistService {

    private final StopRepository stopRepository;
    private final StopUpdateHistoryRepository stopUpdateHistoryRepository;

    /// 정류소 목록과 변경 이력을 하나의 독립 트랜잭션으로 저장한다.
    ///
    /// @param toSave    신규 저장할 정류소 목록
    /// @param histories 업데이트 이력 목록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPage(List<Stop> toSave, List<StopUpdateHistory> histories) {
        stopRepository.saveAll(toSave);
        stopUpdateHistoryRepository.saveAll(histories);
    }
}
package com.media.bus.stop.service;

import com.media.bus.stop.client.SeoulBusApiClient;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.dto.response.StopBulkRegisterResult;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.entity.StopUpdateHistory;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopRegisterService {

    private final SeoulBusApiClient seoulBusApiClient;
    private final StopRepository stopRepository;
    private final StopBulkPersistService stopBulkPersistService;

    /// 서울 열린데이터광장 공공 API에서 전체 버스 정류소를 가져와 DB에 저장한다.
    /// - 신규 stopId → insert
    /// - 기존 stopId + 필드 변경 → Stop 업데이트 + StopUpdateHistory 기록
    /// - 기존 stopId + 변경 없음 → 건너뜀
    /// 인가 처리는 @Authorize + AuthorizeHandlerInterceptor가 Controller 진입 전에 완료합니다.
    /// @Transactional 제거 — StopBulkPersistService가 REQUIRES_NEW로 페이지별 독립 트랜잭션을 생성한다.
    /// 전체 루프를 단일 트랜잭션으로 묶으면 장시간 DB 커넥션 점유 및 중간 실패 시 전체 롤백이 발생한다.
    public StopBulkRegisterResult registerAllFromPublicApi() {

        int totalCount = seoulBusApiClient.fetchTotalCount();
        int pageSize = seoulBusApiClient.getPageSize();
        log.info("서울 공공 API 전체 정류소 수: {}", totalCount);

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (int start = 1; start <= totalCount; start += pageSize) {
            int end = Math.min(start + pageSize - 1, totalCount);
            List<SeoulBusStopRow> rows = seoulBusApiClient.fetchStops(start, end);

            List<String> stopIds = rows.stream().map(SeoulBusStopRow::stopsNo).toList();
            Map<String, Stop> existingMap = stopRepository.findByStopIdIn(stopIds)
                    .stream().collect(Collectors.toMap(Stop::getStopId, s -> s));

            List<Stop> toSave = new ArrayList<>();
            List<StopUpdateHistory> histories = new ArrayList<>();

            for (SeoulBusStopRow row : rows) {
                Stop existing = existingMap.get(row.stopsNo());
                if (existing == null) {
                    toSave.add(Stop.fromPublicApi(row));
                } else {
                    StopUpdateHistory history = existing.applyUpdate(row, ChangeSource.SYSTEM);
                    if (history != null) {
                        histories.add(history);
                    }
                }
            }

            // 페이지 단위 독립 트랜잭션 커밋 — 중간 실패 시 해당 페이지만 롤백
            stopBulkPersistService.persistPage(toSave, histories);

            savedCount += toSave.size();
            updatedCount += histories.size();
            skippedCount += rows.size() - toSave.size() - histories.size();

            log.info("진행: {}/{} — 신규 {}건, 업데이트 {}건, 변경없음 {}건",
                    end, totalCount, toSave.size(), histories.size(),
                    rows.size() - toSave.size() - histories.size());
        }

        log.info("일괄 등록 완료 — 저장: {}건, 업데이트: {}건, 건너뜀: {}건", savedCount, updatedCount, skippedCount);
        return new StopBulkRegisterResult(savedCount, updatedCount, skippedCount, totalCount);
    }
}
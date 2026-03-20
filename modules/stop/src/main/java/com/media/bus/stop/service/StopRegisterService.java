package com.media.bus.stop.service;

import com.media.bus.stop.client.SeoulBusApiClient;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.dto.response.StopBulkRegisterResult;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.repository.StopRepository;
import com.media.bus.stop.valid.StopModifyValiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopRegisterService {

    private final SeoulBusApiClient seoulBusApiClient;
    private final StopRepository stopRepository;
    private final StopModifyValiData stopModifyValiData;

    /**
     * 서울 열린데이터광장 공공 API에서 전체 버스 정류소를 가져와 DB에 저장한다.
     * 이미 등록된 stopId는 건너뛴다.
     *
     * @param token 어드민 Bearer 토큰 (Authorization 헤더 원문)
     */
    @Transactional
    public StopBulkRegisterResult registerAllFromPublicApi(String token) {

        stopModifyValiData.isMemberAuthenticationAdmin(token);

        int totalCount = seoulBusApiClient.fetchTotalCount();
        int pageSize = seoulBusApiClient.getPageSize();
        log.info("서울 공공 API 전체 정류소 수: {}", totalCount);

        int savedCount = 0;
        int skippedCount = 0;

        for (int start = 1; start <= totalCount; start += pageSize) {
            int end = Math.min(start + pageSize - 1, totalCount);
            List<SeoulBusStopRow> rows = seoulBusApiClient.fetchStops(start, end);

            List<Stop> toSave = rows.stream()
                    .filter(row -> !stopRepository.existsByStopId(row.stopsNo()))
                    .map(Stop::fromPublicApi)
                    .toList();

            stopRepository.saveAll(toSave);

            savedCount += toSave.size();
            skippedCount += rows.size() - toSave.size();

            log.info("진행: {}/{} — 이번 배치 저장 {}건, 중복 건너뜀 {}건",
                    end, totalCount, toSave.size(), rows.size() - toSave.size());
        }

        log.info("일괄 등록 완료 — 저장: {}건, 건너뜀: {}건", savedCount, skippedCount);
        return new StopBulkRegisterResult(savedCount, skippedCount, totalCount);
    }
}

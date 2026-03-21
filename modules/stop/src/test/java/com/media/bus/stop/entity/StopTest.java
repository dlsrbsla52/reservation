package com.media.bus.stop.entity;

import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.entity.enums.StopType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StopTest {

    @Test
    void requestOf_USER_소스와_등록자_UUID가_설정된다() {
        UUID memberId = UUID.randomUUID();
        SimpleStopCreateRequest request = new SimpleStopCreateRequest(
                "123", "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE
        );

        Stop stop = Stop.requestOf(request, memberId);

        assertThat(stop.getRegisteredById()).isEqualTo(memberId);
        assertThat(stop.getRegisteredBySource()).isEqualTo(ChangeSource.USER);
        assertThat(stop.getStopId()).isEqualTo("123");
        assertThat(stop.getStopName()).isEqualTo("테스트정류소");
        assertThat(stop.getStopsType()).isEqualTo(StopType.CENTER_LANE);
    }

    @Test
    void fromPublicApi_SYSTEM_소스와_등록자_null이_설정된다() {
        SeoulBusStopRow row = new SeoulBusStopRow("456", "공공API정류소", "126.9", "37.5", "NODE002", "중앙차로");

        Stop stop = Stop.fromPublicApi(row);

        assertThat(stop.getRegisteredById()).isNull();
        assertThat(stop.getRegisteredBySource()).isEqualTo(ChangeSource.SYSTEM);
        assertThat(stop.getStopId()).isEqualTo("456");
    }

    @Test
    void 빌더로_생성된_Stop_updateHistories_초기화_확인() {
        Stop stop = Stop.builder()
                .stopId("789")
                .stopName("빌더정류소")
                .xCrd("127.0")
                .yCrd("37.0")
                .nodeId("NODE003")
                .stopsType(StopType.GENERAL_LANE)
                .registeredBySource(ChangeSource.SYSTEM)
                .build();

        assertThat(stop.getUpdateHistories()).isNotNull().isEmpty();
    }
}
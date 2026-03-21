package com.media.bus.stop.entity;

import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.entity.enums.StopType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StopUpdateHistoryTest {

    private Stop buildStop(String stopId, String name, String xCrd, String yCrd, String nodeId, StopType type) {
        return Stop.builder()
                .stopId(stopId)
                .stopName(name)
                .xCrd(xCrd)
                .yCrd(yCrd)
                .nodeId(nodeId)
                .stopsType(type)
                .registeredBySource(ChangeSource.SYSTEM)
                .build();
    }

    @Test
    void of_Stop_엔티티_관계가_설정된다() {
        Stop stop = buildStop("123", "구정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE);
        SeoulBusStopRow row = new SeoulBusStopRow("123", "신정류소", "127.1", "37.1", "NODE002", "일반차로");

        StopUpdateHistory history = StopUpdateHistory.of(stop, row, StopType.GENERAL_LANE, ChangeSource.SYSTEM);

        assertThat(history.getStop()).isSameAs(stop);
    }

    @Test
    void of_변경_전후_값이_올바르게_기록된다() {
        Stop stop = buildStop("123", "구정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE);
        SeoulBusStopRow row = new SeoulBusStopRow("123", "신정류소", "127.1", "37.1", "NODE002", "일반차로");

        StopUpdateHistory history = StopUpdateHistory.of(stop, row, StopType.GENERAL_LANE, ChangeSource.USER);

        assertThat(history.getOldStopName()).isEqualTo("구정류소");
        assertThat(history.getNewStopName()).isEqualTo("신정류소");
        assertThat(history.getOldStopsType()).isEqualTo(StopType.CENTER_LANE);
        assertThat(history.getNewStopsType()).isEqualTo(StopType.GENERAL_LANE);
        assertThat(history.getChangeSource()).isEqualTo(ChangeSource.USER);
    }

    @Test
    void applyUpdate_변경_있으면_history_반환하고_필드_갱신() {
        Stop stop = buildStop("123", "구정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE);
        SeoulBusStopRow row = new SeoulBusStopRow("123", "신정류소", "127.1", "37.1", "NODE002", "일반차로");

        StopUpdateHistory history = stop.applyUpdate(row, ChangeSource.SYSTEM);

        assertThat(history).isNotNull();
        assertThat(stop.getStopName()).isEqualTo("신정류소");
        assertThat(stop.getStopsType()).isEqualTo(StopType.GENERAL_LANE);
    }

    @Test
    void applyUpdate_변경_없으면_null_반환() {
        Stop stop = buildStop("123", "정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE);
        SeoulBusStopRow row = new SeoulBusStopRow("123", "정류소", "127.0", "37.0", "NODE001", "중앙차로");

        StopUpdateHistory history = stop.applyUpdate(row, ChangeSource.SYSTEM);

        assertThat(history).isNull();
    }
}
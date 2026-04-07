package com.media.bus.stop.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.entity.enums.StopType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stop", schema = "stop")
public class Stop extends DateBaseEntity {

    @Size(max = 50)
    @NotNull
    @Column(name = "stop_id", nullable = false, length = 50)
    private String stopId;

    @Size(max = 50)
    @NotNull
    @Column(name = "stop_name", nullable = false, length = 50)
    private String stopName;

    @Size(max = 50)
    @NotNull
    @Column(name = "x_crd", nullable = false, length = 50)
    private String xCrd;

    @Size(max = 50)
    @NotNull
    @Column(name = "y_crd", nullable = false, length = 50)
    private String yCrd;

    @Size(max = 50)
    @NotNull
    @Column(name = "node_id", nullable = false, length = 50)
    private String nodeId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "stops_type", nullable = false, length = 50)
    private StopType stopsType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "registered_by_id")
    private UUID registeredById;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "registered_by_source", nullable = false, length = 50)
    private ChangeSource registeredBySource;

    @Builder.Default
    @OneToMany(mappedBy = "stop", fetch = FetchType.LAZY)
    private List<StopUpdateHistory> updateHistories = new ArrayList<>();


    /// 공공 API 데이터와 현재 엔티티를 비교해 변경이 있으면 필드를 갱신하고 히스토리 객체를 반환한다.
    /// 변경이 없으면 null을 반환한다.
    public StopUpdateHistory applyUpdate(SeoulBusStopRow row, ChangeSource changeSource) {
        StopType newType = StopType.fromDisplayName(row.stopsType());
        boolean changed = !Objects.equals(this.stopName, row.stopsName())
                || !Objects.equals(this.xCrd, row.xCrd())
                || !Objects.equals(this.yCrd, row.yCrd())
                || !Objects.equals(this.nodeId, row.nodeId())
                || this.stopsType != newType;

        if (!changed) return null;

        StopUpdateHistory history = StopUpdateHistory.of(this, row, newType, changeSource);
        this.stopName = row.stopsName();
        this.xCrd = row.xCrd();
        this.yCrd = row.yCrd();
        this.nodeId = row.nodeId();
        this.stopsType = newType;
        return history;
    }

    public static Stop requestOf(SimpleStopCreateRequest request, UUID registeredById) {
        return Stop.builder()
                .stopId(request.stopId())
                .stopName(request.stopName())
                .xCrd(request.xCrd())
                .yCrd(request.yCrd())
                .nodeId(request.nodeId())
                .stopsType(request.stopsType())
                .registeredById(registeredById)
                .registeredBySource(ChangeSource.USER)
                .build();
    }

    public static Stop fromPublicApi(SeoulBusStopRow row) {
        return Stop.builder()
                .stopId(row.stopsNo())
                .stopName(row.stopsName())
                .xCrd(row.xCrd())
                .yCrd(row.yCrd())
                .nodeId(row.nodeId())
                .stopsType(StopType.fromDisplayName(row.stopsType()))
                .registeredById(null)
                .registeredBySource(ChangeSource.SYSTEM)
                .build();
    }

}

package com.media.bus.stop.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.entity.enums.ChangeSource;
import com.media.bus.stop.entity.enums.StopType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stop_update_history", schema = "stop")
public class StopUpdateHistory extends DateBaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", referencedColumnName = "stop_id", nullable = false)
    private Stop stop;

    @Size(max = 50)
    @Column(name = "old_stop_name", length = 50)
    private String oldStopName;

    @Size(max = 50)
    @Column(name = "new_stop_name", length = 50)
    private String newStopName;

    @Size(max = 50)
    @Column(name = "old_x_crd", length = 50)
    private String oldXCrd;

    @Size(max = 50)
    @Column(name = "new_x_crd", length = 50)
    private String newXCrd;

    @Size(max = 50)
    @Column(name = "old_y_crd", length = 50)
    private String oldYCrd;

    @Size(max = 50)
    @Column(name = "new_y_crd", length = 50)
    private String newYCrd;

    @Size(max = 50)
    @Column(name = "old_node_id", length = 50)
    private String oldNodeId;

    @Size(max = 50)
    @Column(name = "new_node_id", length = 50)
    private String newNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_stops_type", length = 50)
    private StopType oldStopsType;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_stops_type", length = 50)
    private StopType newStopsType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "change_source", nullable = false, length = 50)
    private ChangeSource changeSource;


    public static StopUpdateHistory of(Stop stop, SeoulBusStopRow row, StopType newType, ChangeSource changeSource) {
        return StopUpdateHistory.builder()
                .stop(stop)
                .oldStopName(stop.getStopName())
                .newStopName(row.stopsName())
                .oldXCrd(stop.getXCrd())
                .newXCrd(row.xCrd())
                .oldYCrd(stop.getYCrd())
                .newYCrd(row.yCrd())
                .oldNodeId(stop.getNodeId())
                .newNodeId(row.nodeId())
                .oldStopsType(stop.getStopsType())
                .newStopsType(newType)
                .changeSource(changeSource)
                .build();
    }
}
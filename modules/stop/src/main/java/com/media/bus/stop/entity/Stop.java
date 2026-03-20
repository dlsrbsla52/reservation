package com.media.bus.stop.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
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

    @Size(max = 50)
    @NotNull
    @Column(name = "stops_type", nullable = false, length = 50)
    private String stopsType;


    public static Stop requestOf(SimpleStopCreateRequest request) {
        return Stop.builder()
                .stopId(request.stopId())
                .stopName(request.stopName())
                .xCrd(request.xCrd())
                .yCrd(request.yCrd())
                .nodeId(request.nodeId())
                .stopsType(request.stopsType())
                .build();
    }

    public static Stop fromPublicApi(SeoulBusStopRow row) {
        return Stop.builder()
                .stopId(row.stopsNo())
                .stopName(row.stopsName())
                .xCrd(row.xCrd())
                .yCrd(row.yCrd())
                .nodeId(row.nodeId())
                .stopsType(row.stopsType())
                .build();
    }

}

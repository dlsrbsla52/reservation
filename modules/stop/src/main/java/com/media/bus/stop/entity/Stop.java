package com.media.bus.stop.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
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
    @Column(name = "tm_x", nullable = false, length = 50)
    private String tmX;

    @Size(max = 50)
    @NotNull
    @Column(name = "tm_y", nullable = false, length = 50)
    private String tmY;

    @Size(max = 50)
    @NotNull
    @Column(name = "ars_id", nullable = false, length = 50)
    private String arsId;

    @Size(max = 50)
    @NotNull
    @Column(name = "pos_x", nullable = false, length = 50)
    private String posX;

    @Size(max = 50)
    @NotNull
    @Column(name = "pos_y", nullable = false, length = 50)
    private String posY;

    @Size(max = 50)
    @NotNull
    @Column(name = "stop_name", nullable = false, length = 50)
    private String stopName;
    
    
    public static Stop requestOf(SimpleStopCreateRequest request) {
        return Stop.builder()
                .stopId(request.stopId())
                .tmX(request.tmX())
                .tmY(request.tmY())
                .arsId(request.arsId())
                .posX(request.posX())
                .posY(request.posY())
                .stopName(request.stopName())
                .build();
    }
    
}
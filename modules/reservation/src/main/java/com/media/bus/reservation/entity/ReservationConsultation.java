package com.media.bus.reservation.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reservation_consultation", schema = "reservation")
public class ReservationConsultation extends DateBaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Size(max = 18)
    @NotNull
    @Column(name = "status", nullable = false, length = 18)
    private String status;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;


}
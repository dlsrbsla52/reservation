package com.media.bus.reservation.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "reservation", schema = "reservation")
public class Reservation extends DateBaseEntity {

    @NotNull
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @NotNull
    @Column(name = "stop_id", nullable = false)
    private UUID stopId;

    @NotNull
    @Column(name = "consultation_requested_at", nullable = false)
    private OffsetDateTime consultationRequestedAt;

    @NotNull
    @Column(name = "desired_contract_start_date", nullable = false)
    private LocalDate desiredContractStartDate;

    @OneToMany(mappedBy = "reservation", fetch = FetchType.LAZY)
    private List<ReservationConsultation> reservationConsultations;

}
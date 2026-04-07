package com.media.bus.reservation.reservation.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    /// 예약 생성 팩토리 메서드.
    /// CLAUDE.md "정적 팩토리 메서드로 생성 강제" 규약 적용.
    ///
    /// @param memberId                  예약 회원 ID
    /// @param stopId                    예약 정류소 ID
    /// @param consultationRequestedAt   상담 요청 일시
    /// @param desiredContractStartDate  희망 계약 시작일
    /// @return 저장 전 Reservation 인스턴스
    public static Reservation create(
            UUID memberId,
            UUID stopId,
            OffsetDateTime consultationRequestedAt,
            LocalDate desiredContractStartDate
    ) {
        return Reservation.builder()
                .memberId(memberId)
                .stopId(stopId)
                .consultationRequestedAt(consultationRequestedAt)
                .desiredContractStartDate(desiredContractStartDate)
                .build();
    }
}
package com.media.bus.reservation.contract.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.reservation.contract.dto.request.CreateContractRequest;
import com.media.bus.reservation.contract.entity.enums.PaymentCycle;
import com.media.bus.reservation.contract.entity.enums.PaymentMethod;
import com.media.bus.reservation.contract.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "contract_detail",
    schema = "reservation",
    uniqueConstraints = {
        @UniqueConstraint(name = "contract_detail_contract_id_key", columnNames = {"contract_id"})
    }
)
public class ContractDetail extends DateBaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "pay_amount", precision = 15, scale = 2)
    private BigDecimal payAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_cycle", nullable = false, length = 20)
    private PaymentCycle paymentCycle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'UNPAID'")
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    /// Contract 엔티티와 요청 DTO로부터 ContractDetail을 생성하는 정적 팩토리 메서드.
    /// 초기 납부 상태는 항상 UNPAID입니다.
    ///
    /// @param contract 이미 저장된 Contract 엔티티
    /// @param request  계약 생성 요청 DTO
    /// @return 저장 전 ContractDetail 인스턴스
    public static ContractDetail create(Contract contract, CreateContractRequest request) {
        // 계약 생성 시 납부 상태는 항상 미납(UNPAID)
        return ContractDetail.builder()
                .contract(contract)
                .totalAmount(request.totalAmount())
                .payAmount(request.payAmount())
                .paymentCycle(request.paymentCycle())
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.UNPAID)
                .build();
    }
}
package com.media.bus.reservation.contract.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.reservation.contract.dto.request.CreateContractRequest;
import com.media.bus.reservation.contract.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
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

    @Size(max = 20)
    @NotNull
    @Column(name = "payment_cycle", nullable = false, length = 20)
    private String paymentCycle;

    @Size(max = 30)
    @NotNull
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'UNPAID'")
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

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
        ContractDetail detail = new ContractDetail();
        detail.setContract(contract);
        detail.setTotalAmount(request.totalAmount());
        detail.setPayAmount(request.payAmount());
        // paymentCycle, paymentMethod는 Enum.getName()으로 문자열 변환하여 저장
        detail.setPaymentCycle(request.paymentCycle().getName());
        detail.setPaymentMethod(request.paymentMethod().getName());
        // 계약 생성 시 납부 상태는 항상 미납(UNPAID)
        detail.setPaymentStatus(PaymentStatus.UNPAID.getName());
        return detail;
    }
}
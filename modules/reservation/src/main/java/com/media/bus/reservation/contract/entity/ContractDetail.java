package com.media.bus.reservation.contract.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
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


}
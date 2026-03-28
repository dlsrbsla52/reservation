package com.media.bus.reservation.contract.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "contract",
    schema = "reservation",
    indexes = {
        @Index(name = "idx_contract_stop_id", columnList = "stop_id, status"),
        @Index(name = "idx_contract_previous_contract_id", columnList = "previous_contract_id"),
        @Index(name = "idx_contract_member_id", columnList = "member_id")
    }
)
public class Contract extends DateBaseEntity {

    @NotNull
    @Column(name = "stop_id", nullable = false)
    private UUID stopId;

    @Column(name = "previous_contract_id")
    private UUID previousContractId;

    @NotNull
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Size(max = 300)
    @NotNull
    @Column(name = "contract_name", nullable = false, length = 300)
    private String contractName;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "auto_renewal", nullable = false)
    private Boolean autoRenewal;

    @NotNull
    @Column(name = "contract_start_date", nullable = false)
    private OffsetDateTime contractStartDate;

    @NotNull
    @Column(name = "contract_end_date", nullable = false)
    private OffsetDateTime contractEndDate;

    @Column(name = "renewal_notified_at")
    private OffsetDateTime renewalNotifiedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Size(max = 500)
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToOne(mappedBy = "contract", fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
    private ContractDetail contractDetail;

}
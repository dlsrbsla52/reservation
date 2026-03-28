package com.media.bus.reservation.contract.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import com.media.bus.reservation.contract.dto.request.CreateContractRequest;
import com.media.bus.reservation.contract.dto.response.MemberInfo;
import com.media.bus.reservation.contract.entity.enums.ContractStatus;
import com.media.bus.reservation.reservation.dto.response.StopInfo;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @OneToOne(mappedBy = "contract", cascade = CascadeType.ALL, optional = false)
    private ContractDetail contractDetail;

    /// IAM 회원 정보, 정류소 정보, 요청 DTO로부터 Contract 엔티티를 생성하는 정적 팩토리 메서드.
    /// 계약 초기 상태는 항상 PENDING, autoRenewal은 false로 설정합니다.
    ///
    /// @param memberInfo IAM DB에서 재검증된 회원 정보
    /// @param stopInfo   stop 서비스에서 확인된 정류소 정보
    /// @param request    계약 생성 요청 DTO
    /// @return 저장 전 Contract 인스턴스
    public static Contract create(MemberInfo memberInfo, StopInfo stopInfo, CreateContractRequest request) {
        Contract contract = new Contract();
        contract.setStopId(stopInfo.id());
        contract.setMemberId(memberInfo.id());
        contract.setContractName(request.contractName());
        // 계약 생성 시 초기 상태는 대기(PENDING). 관리자 승인 후 ACTIVE로 전환됩니다.
        contract.setStatus(ContractStatus.PENDING.getName());
        contract.setAutoRenewal(false);
        contract.setContractStartDate(request.contractStartDate());
        contract.setContractEndDate(request.contractEndDate());
        return contract;
    }
}
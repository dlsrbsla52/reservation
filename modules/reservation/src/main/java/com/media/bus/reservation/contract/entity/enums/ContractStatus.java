package com.media.bus.reservation.contract.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/// 계약 상태 Enum.
/// - PENDING: 계약 대기 (초기 생성 시 기본값)
/// - ACTIVE: 계약 활성 (승인 완료)
/// - EXPIRED: 계약 만료 (계약 기간 종료)
/// - CANCELLED: 계약 취소
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum ContractStatus implements BaseEnum {

    PENDING("PENDING", "대기"),
    ACTIVE("ACTIVE", "활성"),
    EXPIRED("EXPIRED", "만료"),
    CANCELLED("CANCELLED", "취소됨");

    private final String name;
    private final String displayName;

    public static Optional<ContractStatus> fromName(String name) {
        return BaseEnum.fromName(ContractStatus.class, name);
    }
}
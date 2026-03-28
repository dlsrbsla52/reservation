package com.media.bus.reservation.contract.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/// 납부 상태 Enum.
/// - UNPAID: 미납 (초기 기본값)
/// - PAID: 납부 완료
/// - OVERDUE: 연체
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum PaymentStatus implements BaseEnum {

    UNPAID("UNPAID", "미납"),
    PAID("PAID", "납부완료"),
    OVERDUE("OVERDUE", "연체");

    private final String name;
    private final String displayName;

    public static Optional<PaymentStatus> fromName(String name) {
        return BaseEnum.fromName(PaymentStatus.class, name);
    }
}
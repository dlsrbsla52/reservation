package com.media.bus.reservation.contract.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/// 납부 주기 Enum.
/// - MONTHLY: 월납
/// - QUARTERLY: 분기납
/// - ANNUALLY: 연납
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum PaymentCycle implements BaseEnum {

    MONTHLY("MONTHLY", "월납"),
    QUARTERLY("QUARTERLY", "분기납"),
    ANNUALLY("ANNUALLY", "연납");

    private final String name;
    private final String displayName;

    public static Optional<PaymentCycle> fromName(String name) {
        return BaseEnum.fromName(PaymentCycle.class, name);
    }
}
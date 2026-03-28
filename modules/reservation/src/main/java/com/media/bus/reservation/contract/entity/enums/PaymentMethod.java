package com.media.bus.reservation.contract.entity.enums;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/// 납부 방법 Enum.
/// - BANK_TRANSFER: 계좌이체
/// - CARD: 카드
/// - CASH: 현금
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum PaymentMethod implements BaseEnum {

    BANK_TRANSFER("BANK_TRANSFER", "계좌이체"),
    CARD("CARD", "카드"),
    CASH("CASH", "현금");

    private final String name;
    private final String displayName;

    public static Optional<PaymentMethod> fromName(String name) {
        return BaseEnum.fromName(PaymentMethod.class, name);
    }
}
package com.hig.entity.member;

import com.hig.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/**
 * 회원 계정 상태 Enum.
 * - ACTIVE : 정상 활성 상태.
 * - SUSPENDED: 관리자에 의해 이용 정지된 상태.
 * - WITHDRAWN: 회원 본인이 자발적으로 탈퇴한 상태.
 *
 * auth 모듈 내부에서만 사용되는 도메인 개념입니다.
 * JWT 클레임에는 포함되지 않으며, 다른 서비스로 노출되지 않습니다.
 */
@Getter
@AllArgsConstructor
public enum MemberStatus implements BaseEnum {
    ACTIVE("ACTIVE", "정상 활성 상태"),
    SUSPENDED("SUSPENDED", "관리자에 의해 이용 정지된 상태"),
    WITHDRAWN("WITHDRAWN", "회원 본인이 자발적으로 탈퇴한 상태");

    public final String name;
    public final String displayName;

    /**
     * Enum 이름으로 검색
     */
    public static Optional<MemberStatus> fromName(String name) {
        return BaseEnum.fromName(MemberStatus.class, name);
    }
}

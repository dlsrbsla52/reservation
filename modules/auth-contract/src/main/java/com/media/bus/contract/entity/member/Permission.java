package com.media.bus.contract.entity.member;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원의 기능별 권한을 정의하는 Enum.
 * MemberType의 defaultPermissions에 묶여 사용되며,
 * @Authorize 어노테이션의 permissions 조건으로 선언적 인가 체크에 활용됩니다.
 *
 * MANAGE는 READ+WRITE+DELETE를 포함하는 최상위 권한으로,
 * MANAGE를 보유한 타입은 모든 Permission 요청을 통과합니다.
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum Permission implements BaseEnum {
    READ("READ", "조회"),
    WRITE("WRITE", "등록/수정"),
    DELETE("DELETE", "삭제"),
    MANAGE("MANAGE", "관리 — READ+WRITE+DELETE 포함"),
    ;

    private final String name;
    private final String displayName;
}

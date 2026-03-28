package com.media.bus.iam.auth.result;

import com.media.bus.common.result.Result;
import lombok.Getter;
import lombok.ToString;

import java.util.function.UnaryOperator;

/**
 * 인증 모듈 전용 결과 코드 Enum.
 * 공통 모듈의 CommonResult(00000~00299)와 코드가 충돌하지 않도록 A 접두사를 사용합니다.
 */
@Getter
@ToString
public enum AuthResult implements Result {

    // 역할(Role) 관련 오류
    ROLE_NOT_FOUND("A0001", "auth.role.not-found.fail.msg", "역할 정보를 찾을 수 없습니다.");

    private final String code;
    private final String messageId;
    private final String message;

    AuthResult(String code, String messageId, String message) {
        this.code = code;
        this.messageId = messageId;
        this.message = message;
    }

    /**
     * 메시지 번들에 등록된 메시지가 있으면 그것을, 없으면 기본 메시지를 반환합니다.
     */
    @Override
    public String getMessage(UnaryOperator<String> operator, String id) {
        String bundleMessage = operator.apply(id);
        if (bundleMessage == null || bundleMessage.isEmpty()) {
            return getMessage();
        }
        return bundleMessage;
    }
}

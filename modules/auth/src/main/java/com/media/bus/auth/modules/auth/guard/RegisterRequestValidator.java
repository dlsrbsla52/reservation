package com.media.bus.auth.modules.auth.guard;

import com.media.bus.auth.modules.auth.dto.RegisterRequest;

/**
 * 회원가입 요청 검증 인터페이스.
 * 패키지 정책에 따라 인터페이스는 상위, 구현체는 impl/ 하위에 위치합니다.
 */
public interface RegisterRequestValidator {

    /**
     * 회원가입 요청의 유효성을 검증합니다.
     * 검증 실패 시 NoAuthenticationException을 던집니다.
     *
     * @param request 회원가입 요청 DTO
     */
    void validate(RegisterRequest request);
}

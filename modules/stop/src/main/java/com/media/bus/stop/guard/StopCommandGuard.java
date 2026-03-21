package com.media.bus.stop.guard;

/**
 * 정류소 명령(CUD) 사전 조건 검증 인터페이스.
 *
 * 인가(Authorization) 체크는 @Authorize 어노테이션 + AuthorizeHandlerInterceptor가 담당하므로
 * Guard는 비즈니스 규칙 검증에만 집중합니다.
 */
public interface StopCommandGuard {

    /** stopId가 이미 등록된 정류소인지 검증합니다. 미등록 시 ServiceException 발생. */
    void isStopRegistered(String stopId);
}

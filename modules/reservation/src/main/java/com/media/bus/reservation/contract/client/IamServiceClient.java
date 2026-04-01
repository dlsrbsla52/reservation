package com.media.bus.reservation.contract.client;

import com.media.bus.reservation.contract.dto.response.MemberInfo;
import com.media.bus.reservation.contract.dto.response.internal.IamMemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/// IAM 서비스 내부 API 클라이언트.
/// 설계 의도:
/// - IamApi(@HttpExchange 프록시)에 HTTP 호출을 위임하고,
///   응답 변환(null 체크) 등 비즈니스 로직을 이 클래스에서 처리한다.
/// - 소비자의 공개 API는 변경하지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class IamServiceClient {

    private final IamApi iamApi;

    /// JWT 토큰으로 IAM DB에서 회원을 조회합니다.
    /// 회원이 존재하지 않거나 응답이 null이면 null을 반환합니다.
    ///
    /// @param jwt 사용자가 전달한 Access JWT 토큰 (Bearer 접두사 제외)
    /// @return 조회된 회원 정보, 없으면 null
    public MemberInfo findMemberByJwt(String jwt) {
        log.debug("[IamServiceClient] JWT로 회원 조회 요청");
        IamMemberResponse response = iamApi.findMemberByJwt(Map.of("jwt", jwt));
        return response != null ? response.data() : null;
    }
}

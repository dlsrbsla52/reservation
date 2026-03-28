package com.media.bus.reservation.contract.client;

import com.media.bus.common.client.BaseServiceClient;
import com.media.bus.common.client.S2SRestClientFactory;
import com.media.bus.reservation.contract.dto.response.MemberInfo;
import com.media.bus.reservation.contract.dto.response.internal.IamMemberResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/// IAM 서비스 내부 API 클라이언트.
/// 설계 의도:
/// - StopServiceClient와 동일한 패턴으로 구성합니다.
/// - S2SRestClientFactory.create()로 생성한 RestClient를 super()에 전달하므로
///   X-Service-Token 헤더는 팩토리 인터셉터가 자동으로 주입합니다.
/// - IAM 모듈 클래스를 직접 참조하지 않습니다.
@Slf4j
@Component
public class IamServiceClient extends BaseServiceClient {

    private static final String MEMBER_JWT_PATH = "/api/v1/member/jwt";

    public IamServiceClient(
            @Value("${services.iam.url}") String iamServiceUrl,
            S2SRestClientFactory s2sRestClientFactory
    ) {
        // S2S 인터셉터가 적용된 RestClient를 상위 클래스에 전달
        super(s2sRestClientFactory.create(iamServiceUrl));
    }

    /// JWT 토큰으로 IAM DB에서 회원을 조회합니다.
    /// 회원이 존재하지 않거나 응답이 null이면 null을 반환합니다.
    ///
    /// @param jwt 사용자가 전달한 Access JWT 토큰 (Bearer 접두사 제외)
    /// @return 조회된 회원 정보, 없으면 null
    public MemberInfo findMemberByJwt(String jwt) {
        log.debug("[IamServiceClient] JWT로 회원 조회 요청");
        IamMemberResponse response = post(
                MEMBER_JWT_PATH,
                Map.of("jwt", jwt),
                IamMemberResponse.class
        );
        return response != null ? response.data() : null;
    }
}
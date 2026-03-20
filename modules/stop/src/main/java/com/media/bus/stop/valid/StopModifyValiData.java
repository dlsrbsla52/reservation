package com.media.bus.stop.valid;


import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopModifyValiData {
    
    private final JwtProvider jwtProvider;
    private final StopRepository stopRepository;

    /**
     * 어드민 권한이 없는 사람은 예외를 던진다.
     * 
     * @param jwtToken 인증 토큰
     */
    public void isMemberAuthenticationAdmin(String jwtToken) {

        MemberPrincipal principal = jwtProvider.getMemberPrincipalFromRefreshToken(jwtToken);
        
        switch (principal.memberType()) {
            case MEMBER, BUSINESS -> throw new ServiceException(CommonResult.AUTHORIZATION_FAIL);
        }
    }

    public void isStopRegistered(String stopId) {
        stopRepository.findByStopId(stopId)
            .orElseThrow(() -> new ServiceException("등록된 정류장을 찾을 수 없습니다."));
    }
    
}

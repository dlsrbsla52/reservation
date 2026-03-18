package com.media.bus.stop.valid;


import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopModifyValiDate {
    
    private final JwtProvider jwtProvider;

    /**
     * 어드민 권한이 없는 사람은 예외를 던진다.
     * 
     * @param jwtToken 인증 토큰
     */
    public void isMemberAuthenticationAdmin(String jwtToken){

        MemberPrincipal principal = jwtProvider.getMemberPrincipalFromRefreshToken(jwtToken);
        
        switch (principal.memberType()) {
            case MEMBER, BUSINESS -> throw new ServiceException(CommonResult.AUTHORIZATION_FAIL);
        }
    }
    
}

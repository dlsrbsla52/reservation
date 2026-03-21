package com.media.bus.stop.guard.impl;

import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.guard.StopCommandGuard;
import com.media.bus.stop.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopCommandGuardImpl implements StopCommandGuard {

    private final JwtProvider jwtProvider;
    private final StopRepository stopRepository;

    @NonNull
    @Override
    public MemberPrincipal isMemberAuthenticationAdmin(String jwtToken) {
        MemberPrincipal principal = jwtProvider.getMemberPrincipalFromRefreshToken(jwtToken);

        switch (principal.memberType()) {
            case MEMBER, BUSINESS -> throw new ServiceException(CommonResult.AUTHORIZATION_FAIL);
        }
        return principal;
    }

    @Override
    public void isStopRegistered(String stopId) {
        stopRepository.findByStopId(stopId)
                .orElseThrow(() -> new ServiceException("등록된 정류장을 찾을 수 없습니다."));
    }
}
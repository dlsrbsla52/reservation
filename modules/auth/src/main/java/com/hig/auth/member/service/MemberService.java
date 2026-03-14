package com.hig.auth.member.service;

import com.hig.auth.member.dto.MemberResponse;
import com.hig.auth.member.entity.Member;
import com.hig.auth.member.repository.MemberRepository;
import com.hig.exceptions.ServiceException;
import com.hig.result.type.CommonResult;
import com.hig.security.JwtProvider;
import com.hig.security.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    /**
     * 로그인시 사용된 jwt 토큰으로 회원 조회.
     *
     * @param jwt jwt 토큰
     * @return MemberResponse
     */
    @Transactional(readOnly = true)
    public MemberResponse findByJwtMember(@Valid String jwt) {
        MemberPrincipal principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt)
        );

        Member member = memberRepository.findById(UUID.fromString(principal.id()))
            .orElseThrow(() -> new ServiceException(CommonResult.USER_NOT_FOUND_FAIL));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            member.getMemberType(),
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }
}

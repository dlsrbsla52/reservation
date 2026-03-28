package com.media.bus.reservation.contract.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/// IAM 서비스 회원 조회 응답의 내부 표현.
/// 설계 의도:
/// - MemberPrincipal은 JWT 클레임 파싱 결과이므로 IAM DB 재검증 결과와 분리합니다.
/// - IAM 응답 구조 변경 시 이 클래스만 수정합니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberInfo(
    UUID id,
    String loginId,
    String email,
    String memberType,
    String status
) {
}
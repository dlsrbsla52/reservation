package com.media.bus.reservation.contract.dto.response.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.media.bus.reservation.contract.dto.response.MemberInfo;

/// IAM 서비스 `/api/v1/member/jwt` 응답 역직렬화용 래퍼 DTO.
/// 설계 의도:
/// - IAM 모듈 클래스를 직접 참조하지 않기 위해 reservation 내부 전용으로 선언합니다.
/// - IAM 응답은 DataView 래퍼(`{"data": {...}}`) 형태이므로 data 필드만 추출합니다.
/// - 알 수 없는 필드는 무시하여 IAM API 응답 변경에 유연하게 대응합니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record IamMemberResponse(MemberInfo data) {
}
package com.media.bus.iam.member.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import java.time.OffsetDateTime

/**
 * ## 회원 조건 검색 파라미터
 *
 * 일반 회원 조회(`AdminUserMemberController`)와 어드민 회원 조회(`AdminMemberController`)가
 * 공유하는 동적 검색 조건 객체.
 *
 * **위치 의도**: `member` 패키지에 두어 `MemberRepository`가 `admin` 패키지에 역의존하지 않도록 한다.
 *
 * **설계 의도**
 * - 컨트롤러에서 허용 카테고리(USER/BUSINESS vs ADMIN)를 별도로 강제하므로,
 *   이 조건만으로는 대상 집단 경계를 넘을 수 없다(권한 경계가 데이터 필터로 새지 않도록).
 * - 모든 필터는 nullable 이며 값이 있는 것만 AND 로 결합한다.
 *
 * **확장 여지(블랙리스트)**
 * - 추후 블랙리스트 후보 필터(노쇼 횟수 등)를 추가할 수 있도록 조건 객체로 분리해 둔다.
 *
 * @property page 페이지 번호(0-base). 음수는 컨트롤러에서 0으로 보정한다.
 * @property size 페이지 크기. 과대 요청은 컨트롤러에서 상한으로 보정한다.
 */
data class MemberSearchCondition(
    /** loginId / email / memberName 부분 일치 키워드 */
    val keyword: String? = null,

    /** 계정 상태 정확 일치 */
    val status: MemberStatus? = null,

    /**
     * 회원 유형 정밀 필터(옵션).
     * 지정하더라도 컨트롤러가 허용한 카테고리를 벗어나면 결과가 비게 된다.
     */
    val type: MemberType? = null,

    /** 사업자번호 정확 일치(비즈니스 회원 식별용) */
    val businessNumber: String? = null,

    /** 가입일 시작(이상) */
    val createdFrom: OffsetDateTime? = null,

    /** 가입일 종료(이하) */
    val createdTo: OffsetDateTime? = null,

    /** 페이지 번호(0-base) */
    val page: Int = 0,

    /** 페이지 크기 */
    val size: Int = 20,
)

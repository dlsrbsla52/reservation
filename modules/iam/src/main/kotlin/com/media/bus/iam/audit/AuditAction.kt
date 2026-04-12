package com.media.bus.iam.audit

/**
 * ## 감사 로그 action 식별자 상수
 *
 * 문자열 오타를 방지하고 기록되는 action 목록을 한 곳에서 관리한다.
 * 새 이벤트 추가 시 상수로 정의하여 일관성을 유지한다.
 */
object AuditAction {
    // 인증/인가
    const val LOGIN = "LOGIN"
    const val LOGOUT = "LOGOUT"
    const val TOKEN_REFRESH = "TOKEN_REFRESH"
    const val REGISTER = "REGISTER"

    // 비밀번호
    const val PASSWORD_CHANGE = "PASSWORD_CHANGE"
    const val PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST"
    const val PASSWORD_RESET_CONFIRM = "PASSWORD_RESET_CONFIRM"

    // 회원 상태
    const val MEMBER_DEACTIVATE = "MEMBER_DEACTIVATE"
    const val MEMBER_REACTIVATE = "MEMBER_REACTIVATE"
    const val MEMBER_WITHDRAW = "MEMBER_WITHDRAW"
    const val MEMBER_SUSPEND = "MEMBER_SUSPEND"
    const val MEMBER_UNSUSPEND = "MEMBER_UNSUSPEND"
    const val MEMBER_MODIFY = "MEMBER_MODIFY"

    // 권한 관리
    const val ROLE_CHANGE = "ROLE_CHANGE"
    const val ROLE_PERMISSION_ASSIGN = "ROLE_PERMISSION_ASSIGN"
    const val ROLE_PERMISSION_REVOKE = "ROLE_PERMISSION_REVOKE"
    const val ADMIN_CREATE = "ADMIN_CREATE"

    // 세션
    const val SESSION_REVOKE = "SESSION_REVOKE"
    const val SESSION_REVOKE_OTHERS = "SESSION_REVOKE_OTHERS"
}

/** 감사 로그 target_type 상수. */
object AuditTargetType {
    const val MEMBER = "MEMBER"
    const val ROLE = "ROLE"
    const val PERMISSION = "PERMISSION"
    const val SESSION = "SESSION"
}

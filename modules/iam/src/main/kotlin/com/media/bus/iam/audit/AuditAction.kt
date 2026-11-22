package com.media.bus.iam.audit

/** 감사 로그 action 식별자 상수 */
object AuditAction {
    const val LOGIN = "LOGIN"
    const val LOGOUT = "LOGOUT"
    const val TOKEN_REFRESH = "TOKEN_REFRESH"
    const val REGISTER = "REGISTER"

    const val PASSWORD_CHANGE = "PASSWORD_CHANGE"
    const val PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST"
    const val PASSWORD_RESET_CONFIRM = "PASSWORD_RESET_CONFIRM"

    const val MEMBER_DEACTIVATE = "MEMBER_DEACTIVATE"
    const val MEMBER_REACTIVATE = "MEMBER_REACTIVATE"
    const val MEMBER_WITHDRAW = "MEMBER_WITHDRAW"
    const val MEMBER_SUSPEND = "MEMBER_SUSPEND"
    const val MEMBER_UNSUSPEND = "MEMBER_UNSUSPEND"
    const val MEMBER_MODIFY = "MEMBER_MODIFY"

    const val ROLE_CHANGE = "ROLE_CHANGE"
    const val ROLE_PERMISSION_ASSIGN = "ROLE_PERMISSION_ASSIGN"
    const val ROLE_PERMISSION_REVOKE = "ROLE_PERMISSION_REVOKE"
    const val ADMIN_CREATE = "ADMIN_CREATE"

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

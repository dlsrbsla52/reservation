package com.media.bus.iam.support

import jakarta.servlet.http.HttpServletRequest

/** 신뢰 프록시 처리는 컨테이너 설정에 위임하며, 외부가 보낸 전달 헤더를 직접 신뢰하지 않는다. */
object ClientIpResolver {
    fun resolve(request: HttpServletRequest?): String? = request?.remoteAddr
}

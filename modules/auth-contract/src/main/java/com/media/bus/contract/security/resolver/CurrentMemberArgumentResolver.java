package com.media.bus.contract.security.resolver;

import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.CurrentMember;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentMember 어노테이션이 붙은 MemberPrincipal 파라미터를 주입하는 ArgumentResolver.
 *
 * MemberPrincipalExtractFilter가 request attribute에 저장한 MemberPrincipal을 꺼내
 * Controller 파라미터에 바인딩합니다.
 *
 * - required=true(기본값): 미인증 시 NoAuthenticationException(401) 발생
 * - required=false: 미인증 시 null 반환 — 선택적 인증 엔드포인트에 활용
 */
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
            && MemberPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        MemberPrincipal principal = (MemberPrincipal) request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY);

        if (principal == null) {
            CurrentMember annotation = parameter.getParameterAnnotation(CurrentMember.class);
            if (annotation != null && annotation.required()) {
                throw new NoAuthenticationException();
            }
            return null;
        }

        return principal;
    }
}

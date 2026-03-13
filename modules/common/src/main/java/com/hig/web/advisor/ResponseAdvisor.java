package com.hig.web.advisor;

import com.hig.configuration.RestConfigProperties;
import com.hig.web.wrapper.ResponseBodyWrapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * ResponseBody(Json) 타입의 응답이 있기 전 실행 클래스.
 * 실제 래핑 로직은 {@link ResponseBodyWrapper} 구현체에 위임한다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ResponseAdvisor implements ResponseBodyAdvice<Object> {

	private final RestConfigProperties restConfigProperties;
	private final List<ResponseBodyWrapper> bodyWrappers;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	@Override
	public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(
			Object body,
			@NonNull MethodParameter returnType,
			@NonNull MediaType selectedContentType,
			@NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
			@NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response
	) {
		if (!isApplicableContentType(selectedContentType) || !isApplicablePath(request.getURI().getPath())) {
			return body;
		}

		return bodyWrappers.stream()
				.filter(wrapper -> wrapper.supports(body))
				.findFirst()
				.map(wrapper -> wrapper.wrap(body))
				.orElse(body);
	}

	private boolean isApplicablePath(String path) {
		String[] patterns = restConfigProperties.getApplyPatterns();
		if (patterns == null) return false;
		for (String pattern : patterns) {
			if (pathMatcher.match(pattern, path)) return true;
		}
		return false;
	}

	private boolean isApplicableContentType(MediaType contentType) {
		return contentType != null
				&& (contentType.includes(MediaType.APPLICATION_JSON) || contentType.includes(MediaType.TEXT_PLAIN));
	}
}

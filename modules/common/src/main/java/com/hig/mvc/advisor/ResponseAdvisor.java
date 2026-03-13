package com.hig.mvc.advisor;

import com.hig.mvc.properties.RestConfigProperties;
import com.hig.mvc.response.DataView;
import com.hig.mvc.response.ErrorView;
import com.hig.mvc.response.ListData;
import com.hig.mvc.response.NoDataView;
import com.hig.mvc.response.PageData;
import com.hig.mvc.response.PageView;
import com.hig.mvc.wrappers.PageResult;
import com.hig.result.type.CommonResult;
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
 * ResponseBody(Json) 타입의 응답이 있기 전 실행 클래스
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ResponseAdvisor implements ResponseBodyAdvice<Object> {

	private final RestConfigProperties restConfigProperties;

	@Override
	public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object beforeBodyWrite(
        Object body,
        @NonNull MethodParameter returnType,
        @NonNull MediaType selectedContentType,
        @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response
    ) {
		
		if (isApplyRestServiceContentType(selectedContentType) && isApplyRestServicePattern(request.getURI().getPath())) {
			if (body == null) {
				return DataView.builder()
						.result(CommonResult.REQUEST_SUCCESS)
						.data(body).build();
				
			} else if (body.getClass() == PageView.class || body.getClass() == DataView.class
						|| body.getClass() == ErrorView.class || body.getClass() == NoDataView.class) {
				return body;
				
			} else if (body.getClass() == PageResult.class) {
				PageResult<?> result = (PageResult<?>) body;
				return PageView.builder()
							.result(CommonResult.REQUEST_SUCCESS)
							.data(ListData.builder()
										.pageData(PageData.builder()
														.pageNum(result.getPageNum())
														.pageRows(result.getPageRows())
														.totalCnt(result.getTotalCnt())
														.build())
										.list((List<Object>) result)
										.build()).build();
				
			} else {
				if(!(body instanceof String)) {
					return DataView.builder()
							.result(CommonResult.REQUEST_SUCCESS)
							.data(body).build();
				}
			}
		}
		return body;
	}

	/*
	 * Request URI가 Rest API의 패턴과 일치하는지 여부를 리턴
	 */
	private boolean isApplyRestServicePattern(String path) {
		AntPathMatcher antPathMatcher = new AntPathMatcher();
		String[] patterns = restConfigProperties.getApplyPatterns();
		boolean result = false;
		if (patterns != null) {
			for(String pattern : patterns) {
				result = antPathMatcher.match(pattern, path);
				if (result) {
					break;
				}
			}
		}
		return result;
	}
	
	private boolean isApplyRestServiceContentType(MediaType selectedContentType) {
		if (selectedContentType == null) {
			return false;
		}
		
		return selectedContentType.includes(MediaType.APPLICATION_JSON) || 
				selectedContentType.includes(MediaType.TEXT_PLAIN);
	}
	
}

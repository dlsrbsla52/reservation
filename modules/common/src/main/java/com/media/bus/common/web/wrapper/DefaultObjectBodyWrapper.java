package com.media.bus.common.web.wrapper;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import org.springframework.core.annotation.Order;

/// 위 Wrapper 중 어느 것도 처리하지 않은 일반 객체를 DataView로 래핑하는 기본 Wrapper.
/// 항상 마지막에 실행되도록 Order를 가장 낮은 우선순위로 설정한다.
@Order(Integer.MAX_VALUE)
public class DefaultObjectBodyWrapper implements ResponseBodyWrapper {

	@Override
	public boolean supports(Object body) {
		return true;
	}

	@Override
	public Object wrap(Object body) {
		return DataView.builder()
				.result(CommonResult.REQUEST_SUCCESS)
				.data(body)
				.build();
	}
}

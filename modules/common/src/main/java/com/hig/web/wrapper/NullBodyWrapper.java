package com.hig.web.wrapper;

import com.hig.result.type.CommonResult;
import com.hig.web.response.NoDataView;
import org.springframework.core.annotation.Order;

/**
 * null body를 NoDataView로 래핑하는 Wrapper.
 */
@Order(2)
public class NullBodyWrapper implements ResponseBodyWrapper {

	@Override
	public boolean supports(Object body) {
		return body == null;
	}

	@Override
	public Object wrap(Object body) {
		return NoDataView.builder()
				.result(CommonResult.REQUEST_SUCCESS)
				.build();
	}
}

package com.hig.mvc.wrapper;

import com.hig.mvc.response.AbstractView;
import org.springframework.core.annotation.Order;

/**
 * 이미 AbstractView로 래핑되었거나 String인 경우 그대로 통과시키는 Wrapper.
 * String을 래핑하면 StringHttpMessageConverter가 ClassCastException을 던지므로 반드시 패스스루 처리해야 한다.
 */
@Order(1)
public class PassthroughBodyWrapper implements ResponseBodyWrapper {

	@Override
	public boolean supports(Object body) {
		return body instanceof AbstractView || body instanceof String;
	}

	@Override
	public Object wrap(Object body) {
		return body;
	}
}

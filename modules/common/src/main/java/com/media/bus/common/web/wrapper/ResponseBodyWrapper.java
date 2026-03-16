package com.media.bus.common.web.wrapper;

/**
 * ResponseBody 래핑 전략 인터페이스.
 * 새로운 응답 타입이 필요할 경우 이 인터페이스를 구현하고 Bean으로 등록하면 된다.
 */
public interface ResponseBodyWrapper {

	/**
	 * 이 Wrapper가 해당 body를 처리할 수 있는지 여부를 반환한다.
	 */
	boolean supports(Object body);

	/**
	 * body를 적절한 View로 래핑하여 반환한다.
	 */
	Object wrap(Object body);
}

package com.common.web.response;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 일반적인 Data를 담은 Rest View
 */
@ToString(callSuper = true)
@SuperBuilder
public class DataView<T> extends AbstractView {

	@Getter
	private final T data;
}

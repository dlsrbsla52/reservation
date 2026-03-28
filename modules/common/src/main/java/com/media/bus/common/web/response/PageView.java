package com.media.bus.common.web.response;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// Page 스타일의 Data View
@ToString(callSuper = true)
@SuperBuilder
public class PageView<E> extends AbstractView {

	@Getter
	private final ListData<E> data;
}

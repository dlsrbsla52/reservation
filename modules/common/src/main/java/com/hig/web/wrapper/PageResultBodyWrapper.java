package com.hig.web.wrapper;

import com.hig.result.type.CommonResult;
import com.hig.web.response.ListData;
import com.hig.web.response.PageData;
import com.hig.web.response.PageView;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * PageResult를 PageView로 래핑하는 Wrapper.
 */
@Order(3)
@SuppressWarnings("unchecked")
public class PageResultBodyWrapper implements ResponseBodyWrapper {

	@Override
	public boolean supports(Object body) {
		return body instanceof PageResult;
	}

	@Override
	public Object wrap(Object body) {
		PageResult<?> pageResult = (PageResult<?>) body;
		return PageView.builder()
				.result(CommonResult.REQUEST_SUCCESS)
				.data(ListData.builder()
						.pageData(PageData.builder()
								.pageNum(pageResult.getPageNum())
								.pageRows(pageResult.getPageRows())
								.totalCnt(pageResult.getTotalCnt())
								.build())
						.list((List<Object>) pageResult.getItems())
						.build())
				.build();
	}
}

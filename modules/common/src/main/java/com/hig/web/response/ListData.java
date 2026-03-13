package com.hig.web.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * List 형태의 정보
 */
@Data
@Builder
public class ListData<E> {
	
	private PageData pageData;
	
	private List<E> list;

}

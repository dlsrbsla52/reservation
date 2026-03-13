package com.hig.web.response;

import lombok.Builder;
import lombok.Data;

/**
 * Page에 대한 메타 정보
 */
@Data
@Builder
public class PageData {
	
	private long totalCnt;
	
	private int pageRows;
	
	private int pageNum;

}

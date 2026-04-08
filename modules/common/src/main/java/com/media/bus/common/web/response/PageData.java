package com.media.bus.common.web.response;

/// 페이지 메타 정보
public record PageData(long totalCnt, int pageRows, int pageNum) {}
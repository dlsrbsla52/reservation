package com.media.bus.common.web.response;

import java.util.List;

/// List 형태의 응답 데이터
public record ListData<E>(PageData pageData, List<E> list) {}
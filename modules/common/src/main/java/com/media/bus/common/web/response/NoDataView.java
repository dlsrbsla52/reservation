package com.media.bus.common.web.response;

import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Data가 없는 Rest View
 */
@ToString(callSuper = true)
@SuperBuilder
public class NoDataView extends AbstractView {
}

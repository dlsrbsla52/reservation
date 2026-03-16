package com.media.bus.common.result;

import java.io.Serializable;
import java.util.function.UnaryOperator;

public interface Result extends Serializable {

    String getCode();
	
    String getMessageId();
	
    String getMessage();
	
    String getMessage(UnaryOperator<String> operator, String id);

}

package com.hig.types;

import java.io.Serializable;
import java.util.function.UnaryOperator;

public interface Result extends Serializable {

	abstract String getCode();
	
	abstract String getMessageId();
	
	abstract String getMessage();
	
	abstract String getMessage(UnaryOperator<String> operator, String id);

}

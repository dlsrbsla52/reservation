package com.hig.mvc.advisor;

import com.hig.exceptions.BaseException;
import com.hig.exceptions.NoAuthenticationException;
import com.hig.exceptions.NoAuthorizationException;
import com.hig.mvc.response.ErrorView;
import com.hig.types.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Date;

/**
 * Custom Exception Handler
 */
@Slf4j
@ControllerAdvice
public class ExceptionAdvisor {
	
	/**
	 * NoSuchKeyException Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = NoSuchKeyException.class)
	public @ResponseBody ResponseEntity<ErrorView> noS3KeyHandler(HttpServletRequest request, NoSuchKeyException error) {
		log.error("noS3KeyHandler.error : ", error);
		
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ErrorView.builder()
						.result(CommonResult.STORAGE_NOTFOUND_ERROR)
						.status(HttpStatus.NOT_FOUND.value())
						.error(HttpStatus.NOT_FOUND.getReasonPhrase())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}
	
	/** 
	 * AccessDeniedException Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = AccessDeniedException.class)
	public @ResponseBody ResponseEntity<ErrorView> accessDeniedHandler(HttpServletRequest request, AccessDeniedException error) {
		log.error("accessDeniedHandler.error : ", error);
		
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ErrorView.builder()
						.result(CommonResult.AUTHORIZATION_FAIL)
						.status(HttpStatus.FORBIDDEN.value())
						.error(HttpStatus.FORBIDDEN.getReasonPhrase())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}
	
	/**
	 * Exception Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = Exception.class)
	public @ResponseBody ResponseEntity<ErrorView> defaultHandler(HttpServletRequest request, Exception error) {
		log.error("defaultHandler.error : ", error);
		
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorView.builder()
						.result(CommonResult.ERROR)
						.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
						.error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}

	/**
	 * BaseException Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = BaseException.class)
	public @ResponseBody ResponseEntity<ErrorView> baseHandler(HttpServletRequest request, BaseException error) {
		log.error("baseHandler.error : ", error);
		
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorView.builder()
						.result(error.getResult())
						.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
						.error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
						.message(error.getMessage())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}
	
	/**
	 * NoAuthenticationException Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = NoAuthenticationException.class)
	public @ResponseBody ResponseEntity<ErrorView> noAuthenticationExceptionHandler(HttpServletRequest request, 
			NoAuthenticationException error) {
		log.error("noAuthenticationExceptionHandler.error : ", error);

		return ResponseEntity.status(HttpStatus.OK)
				.body(ErrorView.builder()
						.result(error.getResult())
						.status(HttpStatus.OK.value())
						.error(HttpStatus.OK.getReasonPhrase())
						.message(error.getMessage())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}
	
	/**
	 * NoAuthorizationException Handler
	 * 
	 * @param error
	 * @return ResponseEntity<ErrorView>
	 */
	@ExceptionHandler(value = NoAuthorizationException.class)
	public @ResponseBody ResponseEntity<ErrorView> noAuthorizationExceptionHandler(HttpServletRequest request, NoAuthorizationException error) {
		log.error("noAuthorizationExceptionHandler.error : ", error);
		
		return ResponseEntity.status(HttpStatus.OK)
				.body(ErrorView.builder()
						.result(error.getResult())
						.status(HttpStatus.OK.value())
						.error(HttpStatus.OK.getReasonPhrase())
						.message(error.getMessage())
						.timestamp(new Date())
						.path(request.getServletPath())
						.build());
	}
	
}

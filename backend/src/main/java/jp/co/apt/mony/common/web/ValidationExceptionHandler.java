package jp.co.apt.mony.common.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jp.co.apt.mony.common.validation.ValidationFailedException;

/** ValidationFailedException を 400 {errors:[{field,type}]}（ログインAPIと同じ形式）に変換する。 */
@RestControllerAdvice
public class ValidationExceptionHandler {

	@ExceptionHandler(ValidationFailedException.class)
	ResponseEntity<Map<String, List<Map<String, String>>>> handle(ValidationFailedException e) {
		List<Map<String, String>> errors = e.result().errors().stream()
				.map(error -> Map.of("field", error.field(), "type", error.type().name())).toList();
		return ResponseEntity.badRequest().body(Map.of("errors", errors));
	}

}

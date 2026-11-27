package jp.co.apt.mony.user;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** ユーザーAPIの 409（DUPLICATE / SELF / LAST_ADMIN / HAS_RESULTS）と 404（NOT_FOUND）。 */
@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

	@ExceptionHandler(UserConflictException.class)
	ResponseEntity<Map<String, String>> conflict(UserConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.reason().name()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(UserNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
	}

}

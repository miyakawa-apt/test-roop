package jp.co.apt.mony.location;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** ロケーションAPIの 409（DUPLICATE / HAS_STOCK）と 404（NOT_FOUND）。 */
@RestControllerAdvice(assignableTypes = LocationController.class)
public class LocationExceptionHandler {

	@ExceptionHandler(LocationConflictException.class)
	ResponseEntity<Map<String, String>> conflict(LocationConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.reason().name()));
	}

	@ExceptionHandler(LocationNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(LocationNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
	}

}

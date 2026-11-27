package jp.co.apt.mony.product;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 商品APIの 409（DUPLICATE / HAS_STOCK）と 404（NOT_FOUND）。 */
@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

	@ExceptionHandler(ProductConflictException.class)
	ResponseEntity<Map<String, String>> conflict(ProductConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.reason().name()));
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(ProductNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
	}

}

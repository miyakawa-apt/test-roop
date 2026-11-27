package jp.co.apt.mony.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 商品マスタAPI（管理者のみ。/api/admin/** は SecurityConfig で管理者に限定している）。 */
@RestController
@RequestMapping("/api/admin/products")
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@GetMapping
	public List<ProductResponse> list() {
		return service.list().stream().map(ProductResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse register(@RequestBody(required = false) ProductRequest body) {
		return ProductResponse.of(service.register(body));
	}

	@PutMapping("/{code}")
	public ProductResponse update(@PathVariable String code, @RequestBody(required = false) ProductRequest body) {
		return ProductResponse.of(service.update(code, body));
	}

	@DeleteMapping("/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String code) {
		service.delete(code);
	}

}

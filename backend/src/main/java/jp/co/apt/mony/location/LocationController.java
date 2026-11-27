package jp.co.apt.mony.location;

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

/** ロケーションAPI（管理者のみ。/api/admin/** は SecurityConfig で管理者に限定している）。 */
@RestController
@RequestMapping("/api/admin/locations")
public class LocationController {

	private final LocationService service;

	public LocationController(LocationService service) {
		this.service = service;
	}

	@GetMapping
	public List<LocationResponse> list() {
		return service.list().stream().map(LocationResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LocationResponse register(@RequestBody(required = false) LocationRequest body) {
		return LocationResponse.of(service.register(body));
	}

	/** パスが変更前の棚番、本文の shelfNo が新しい棚番。 */
	@PutMapping("/{shelfNo}")
	public LocationResponse update(@PathVariable String shelfNo, @RequestBody(required = false) LocationRequest body) {
		return LocationResponse.of(service.update(shelfNo, body));
	}

	@DeleteMapping("/{shelfNo}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String shelfNo) {
		service.delete(shelfNo);
	}

}

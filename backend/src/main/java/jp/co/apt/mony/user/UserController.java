package jp.co.apt.mony.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jp.co.apt.mony.auth.AppUser;

/** ユーザーAPI（管理者のみ。/api/admin/** は SecurityConfig で管理者に限定している）。 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

	private final UserService service;

	public UserController(UserService service) {
		this.service = service;
	}

	@GetMapping
	public List<UserResponse> list() {
		return service.list().stream().map(UserResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@RequestBody(required = false) UserRequest body) {
		return UserResponse.of(service.register(body));
	}

	@PutMapping("/{userId}")
	public UserResponse update(@PathVariable String userId, @RequestBody(required = false) UserRequest body) {
		return UserResponse.of(service.update(userId, body));
	}

	/** ログイン中の本人は削除できない（認証情報の AppUser から本人を判定する）。 */
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String userId, @AuthenticationPrincipal AppUser current) {
		service.delete(userId, current.getUserId());
	}

}

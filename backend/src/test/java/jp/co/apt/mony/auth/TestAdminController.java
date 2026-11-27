package jp.co.apt.mony.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 権限制御の確認用（テスト専用。本番コードには置かない）。 */
@RestController
public class TestAdminController {

	@GetMapping("/api/admin/test-only")
	public String adminOnly() {
		return "ok";
	}

	@GetMapping("/api/test-only/any-user")
	public String anyLoggedInUser() {
		return "ok";
	}

}

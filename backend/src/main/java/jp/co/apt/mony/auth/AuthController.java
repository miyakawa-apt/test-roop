package jp.co.apt.mony.auth;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.apt.mony.common.validation.InputRule;
import jp.co.apt.mony.common.validation.ValidationResult;

/** ログインと現在のログイン者取得（ログアウトは SecurityConfig の logout で処理する）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final SecurityContextRepository securityContextRepository;

	public AuthController(AuthenticationManager authenticationManager,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository) {
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.securityContextRepository = securityContextRepository;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		String userId = body == null ? null : body.userId();
		String inputPassword = body == null ? null : body.password();

		ValidationResult validation = new ValidationResult();
		InputRule.USER_ID.validate(validation, userId);
		InputRule.PASSWORD.validate(validation, inputPassword);
		if (!validation.isValid()) {
			List<Map<String, String>> errors = validation.errors().stream()
					.map(e -> Map.of("field", e.field(), "type", e.type().name())).toList();
			return ResponseEntity.badRequest().body(Map.of("errors", errors));
		}

		Authentication authentication;
		try {
			authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(userId, inputPassword));
		} catch (AuthenticationException e) {
			// ID 不在・パスワード不一致を区別しない同一応答
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "AUTHENTICATION_FAILED"));
		}

		// セッション固定化攻撃対策（既存セッションがあればセッションIDを変更）
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		return ResponseEntity.ok(CurrentUserResponse.of((AppUser) authentication.getPrincipal()));
	}

	@GetMapping("/me")
	public CurrentUserResponse me(Authentication authentication) {
		return CurrentUserResponse.of((AppUser) authentication.getPrincipal());
	}

}

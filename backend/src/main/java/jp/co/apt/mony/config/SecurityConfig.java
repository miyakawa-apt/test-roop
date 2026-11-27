package jp.co.apt.mony.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.DispatcherType;
import jp.co.apt.mony.common.code.Role;

/**
 * セキュリティ設定。ログイン状態は HTTP セッション（Spring Session JDBC で DB に保存）、
 * CSRF は Cookie 方式（XSRF-TOKEN Cookie ＋ X-XSRF-TOKEN ヘッダー）。
 * 未ログインは 401、権限不足と CSRF 不正は 403。
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			CsrfTokenRequestAttributeHandler csrfTokenRequestHandler,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						// sendError による 403 等が /error へ転送される際に、401 へ化けないようにする
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/health", "/api/auth/login", "/", "/index.html", "/assets/**",
								"/favicon.ico")
						.permitAll()
						.requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(csrfTokenRequestHandler))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	/**
	 * SPA 向け。BREACH 対策の XOR は使わず、Cookie の値をそのままヘッダーに載せる方式にする。
	 * トークンは遅延読み込み（使うまで Cookie が書かれない）なので、CsrfCookieFilter で毎リクエスト読み込み、
	 * GET でも XSRF-TOKEN Cookie を返す。
	 */
	@Bean
	CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
		return new CsrfTokenRequestAttributeHandler();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** ログイン成功時: 既存セッションがあればセッションIDを変更する（セッション固定化攻撃対策）。 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new ChangeSessionIdAuthenticationStrategy();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}

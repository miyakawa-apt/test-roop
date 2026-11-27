package jp.co.apt.mony.config;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 遅延読み込みの CSRF トークンを毎リクエスト読み込み、XSRF-TOKEN Cookie を必ず返す（SPA 向け）。
 * Cookie が無い・消された（ログアウト直後など）ときも、次のリクエストで新しい Cookie が発行される。
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// 値を読むことでトークンが読み込まれ、Cookie がレスポンスに載る
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}

}

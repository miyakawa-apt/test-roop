package jp.co.apt.mony.auth;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * テスト用の CSRF 付与。XSRF-TOKEN Cookie と X-XSRF-TOKEN ヘッダーに同じ値を載せる（画面側の使い方と同じ）。
 * spring-security-test の csrf() はフィルターの設定を書き換えて他のテストに影響するため使わない。
 */
public final class CsrfSupport {

	private static final String TOKEN = "test-csrf-token";

	private CsrfSupport() {
	}

	public static RequestPostProcessor withCsrf() {
		return request -> {
			Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
			Cookie[] merged = java.util.Arrays.copyOf(existing, existing.length + 1);
			merged[existing.length] = new Cookie("XSRF-TOKEN", TOKEN);
			request.setCookies(merged);
			request.addHeader("X-XSRF-TOKEN", TOKEN);
			return request;
		};
	}

}

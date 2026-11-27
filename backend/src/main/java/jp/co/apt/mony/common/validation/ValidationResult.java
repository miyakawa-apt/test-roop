package jp.co.apt.mony.common.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 検証結果。複数のエラーをまとめて保持する。 */
public final class ValidationResult {

	private final List<ValidationError> errors = new ArrayList<>();

	public void add(ValidationError error) {
		errors.add(error);
	}

	public boolean isValid() {
		return errors.isEmpty();
	}

	public List<ValidationError> errors() {
		return Collections.unmodifiableList(errors);
	}

	/** 指定項目のエラー種別一覧。 */
	public List<ErrorType> typesOf(String field) {
		return errors.stream().filter(e -> e.field().equals(field)).map(ValidationError::type).toList();
	}

	@Override
	public String toString() {
		return "ValidationResult" + errors;
	}

}

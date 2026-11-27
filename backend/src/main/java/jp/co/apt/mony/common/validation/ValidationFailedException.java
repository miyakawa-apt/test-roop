package jp.co.apt.mony.common.validation;

/** 入力検証エラー（複数項目まとめて）。API では 400 と {errors:[{field,type}]} になる。 */
public class ValidationFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient ValidationResult result;

	public ValidationFailedException(ValidationResult result) {
		super(result.toString());
		this.result = result;
	}

	public ValidationResult result() {
		return result;
	}

}

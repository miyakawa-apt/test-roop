package jp.co.apt.mony.common.code;

/** 区分値 enum 共通の文字列変換。 */
final class CodeSupport {

	private CodeSupport() {
	}

	/** enum 定数名（完全一致・大文字小文字区別あり）から変換する。不正値は IllegalArgumentException。 */
	static <E extends Enum<E>> E parse(Class<E> type, String value) {
		if (value == null) {
			throw new IllegalArgumentException(type.getSimpleName() + " に null は指定できません");
		}
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(value)) {
				return constant;
			}
		}
		throw new IllegalArgumentException(type.getSimpleName() + " の不正な値です: " + value);
	}

}

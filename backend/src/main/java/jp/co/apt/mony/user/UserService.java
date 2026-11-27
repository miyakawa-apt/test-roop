package jp.co.apt.mony.user;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.InputRule;
import jp.co.apt.mony.common.validation.ValidationError;
import jp.co.apt.mony.common.validation.ValidationFailedException;
import jp.co.apt.mony.common.validation.ValidationResult;

/** ユーザーの業務処理（FR-09）。登録・更新・削除はそれぞれ1トランザクション。パスワードはハッシュ化して保存する。 */
@Service
public class UserService {

	private static final UserRequest EMPTY = new UserRequest(null, null, null, null);

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<UserAccount> list() {
		return repository.findAllByOrderByUserIdAsc();
	}

	@Transactional
	public UserAccount register(UserRequest request) {
		UserRequest r = request == null ? EMPTY : request;
		ValidationResult validation = new ValidationResult();
		InputRule.USER_ID.validate(validation, r.userId());
		InputRule.USER_NAME.validate(validation, r.userName());
		InputRule.PASSWORD.validate(validation, r.password());
		validateRole(validation, r.role());
		throwIfInvalid(validation);

		if (repository.existsById(r.userId())) {
			throw new UserConflictException(UserConflictException.Reason.DUPLICATE);
		}
		try {
			return repository.saveAndFlush(new UserAccount(r.userId(), r.userName(),
					passwordEncoder.encode(r.password()), Role.fromName(r.role())));
		} catch (DataIntegrityViolationException e) {
			// 同時登録で先に入れられた場合
			throw new UserConflictException(UserConflictException.Reason.DUPLICATE);
		}
	}

	/**
	 * ユーザーID以外を更新する（要求の userId は無視）。パスワードが空（null・空文字）なら変更しない。
	 * 最後の管理者を一般へ変更することはできない。
	 */
	@Transactional
	public UserAccount update(String userId, UserRequest request) {
		UserRequest r = request == null ? EMPTY : request;
		boolean changePw = r.password() != null && !r.password().isEmpty();
		ValidationResult validation = new ValidationResult();
		InputRule.USER_NAME.validate(validation, r.userName());
		if (changePw) {
			InputRule.PASSWORD.validate(validation, r.password());
		}
		validateRole(validation, r.role());
		throwIfInvalid(validation);

		UserAccount user = repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
		Role newRole = Role.fromName(r.role());
		if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN && isLastAdmin()) {
			throw new UserConflictException(UserConflictException.Reason.LAST_ADMIN);
		}
		user.change(r.userName(), newRole);
		if (changePw) {
			user.changePasswordHash(passwordEncoder.encode(r.password()));
		}
		try {
			return repository.saveAndFlush(user);
		} catch (OptimisticLockingFailureException e) {
			// 確認の直後に他の人に削除された場合
			throw new UserNotFoundException(userId);
		}
	}

	/**
	 * 削除する。ログイン中の本人（currentUserId）・最後の管理者・実績の履歴があるユーザーは削除できない。
	 */
	@Transactional
	public void delete(String userId, String currentUserId) {
		UserAccount user = repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
		if (userId.equals(currentUserId)) {
			throw new UserConflictException(UserConflictException.Reason.SELF);
		}
		if (user.getRole() == Role.ADMIN && isLastAdmin()) {
			throw new UserConflictException(UserConflictException.Reason.LAST_ADMIN);
		}
		if (repository.countResultRows(userId) > 0) {
			throw new UserConflictException(UserConflictException.Reason.HAS_RESULTS);
		}
		try {
			repository.delete(user);
			repository.flush();
		} catch (OptimisticLockingFailureException e) {
			// 確認の直後に他の人に削除された場合
			throw new UserNotFoundException(userId);
		} catch (DataIntegrityViolationException e) {
			// 確認の直後に実績が作られた場合（実績の外部キー違反）
			throw new UserConflictException(UserConflictException.Reason.HAS_RESULTS);
		}
	}

	/** 管理者が1人以下か（管理者の行をロックして数える）。 */
	private boolean isLastAdmin() {
		return repository.lockAllByRole(Role.ADMIN).size() <= 1;
	}

	private static void validateRole(ValidationResult validation, String role) {
		if (role == null || role.isEmpty()) {
			validation.add(new ValidationError("role", ErrorType.REQUIRED, "role の検証エラー: REQUIRED"));
			return;
		}
		try {
			Role.fromName(role);
		} catch (IllegalArgumentException e) {
			validation.add(new ValidationError("role", ErrorType.INVALID_FORMAT, "role の検証エラー: INVALID_FORMAT"));
		}
	}

	private static void throwIfInvalid(ValidationResult validation) {
		if (!validation.isValid()) {
			throw new ValidationFailedException(validation);
		}
	}

}

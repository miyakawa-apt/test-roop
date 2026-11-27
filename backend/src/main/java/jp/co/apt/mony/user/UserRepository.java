package jp.co.apt.mony.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jp.co.apt.mony.common.code.Role;

public interface UserRepository extends JpaRepository<UserAccount, String> {

	/** ユーザーID順の一覧。 */
	List<UserAccount> findAllByOrderByUserIdAsc();

	/** 指定した権限のユーザーを行ロックして取る（最後の管理者の判定を同時操作から守る）。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM UserAccount u WHERE u.role = :role")
	List<UserAccount> lockAllByRole(@Param("role") Role role);

	/** そのユーザーの実績（work_result）の行数。 */
	@Query(value = "SELECT count(*) FROM work_result WHERE user_id = :userId", nativeQuery = true)
	long countResultRows(@Param("userId") String userId);

}

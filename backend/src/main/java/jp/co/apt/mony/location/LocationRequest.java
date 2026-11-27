package jp.co.apt.mony.location;

/**
 * 登録・更新の要求。値は文字列で受け、InputRule で検証する。
 * shelfStatus は定数名（EMPTY / PROHIBITED）。更新では shelfNo が「新しい棚番」で、変更前の棚番はパスで指定する。
 * 更新で実棚（STOCKED）の行を変えないときに限り、shelfStatus に STOCKED を受け付ける（備考だけの更新）。
 */
public record LocationRequest(String shelfNo, String shelfStatus, String remarks) {
}

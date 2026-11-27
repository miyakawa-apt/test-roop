// API クライアント（fetch）。CSRF ヘッダーの自動付与と JSON の読み書きを行う。
// HTTP のエラー状態（400/401 など）は例外にせず status と body で返す。通信失敗だけが例外になる。

export interface ApiResponse<T = unknown> {
  status: number
  ok: boolean
  /** JSON の本文。本文なし・JSON でないときは undefined。 */
  body: T | undefined
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
/** XSRF-TOKEN Cookie を得るためだけに呼ぶ GET。 */
const CSRF_SEED_PATH = '/api/health'

function readCsrfToken(): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith(`${CSRF_COOKIE}=`))
  return entry ? decodeURIComponent(entry.slice(CSRF_COOKIE.length + 1)) : undefined
}

async function send<T>(method: string, path: string, body?: unknown, csrf?: string): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (csrf) headers[CSRF_HEADER] = csrf
  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let parsed: T | undefined
  if (res.status !== 204) {
    try {
      parsed = (await res.json()) as T
    } catch {
      parsed = undefined
    }
  }
  return { status: res.status, ok: res.ok, body: parsed }
}

/** GET。 */
export function apiGet<T = unknown>(path: string): Promise<ApiResponse<T>> {
  return send<T>('GET', path)
}

/**
 * 変更系（POST・PUT・DELETE）。XSRF-TOKEN Cookie が無ければ先に GET で取得し（ログアウト直後など）、
 * X-XSRF-TOKEN ヘッダーに載せる。
 */
export async function apiSend<T = unknown>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<ApiResponse<T>> {
  let token = readCsrfToken()
  if (!token) {
    await send('GET', CSRF_SEED_PATH)
    token = readCsrfToken()
  }
  return send<T>(method, path, body, token)
}

export const apiPost = <T = unknown>(path: string, body?: unknown) => apiSend<T>('POST', path, body)

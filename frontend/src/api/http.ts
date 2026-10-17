// Cliente HTTP da SPA: mesma origem do Spring, sessão por cookie (POST /api/auth/login).
// Erros do back-end chegam como ErroResponse {status, erro, mensagem, detalhes[]}.

export class ApiError extends Error {
  status: number
  detalhes: string[]
  constructor(status: number, mensagem: string, detalhes: string[] = []) {
    super(mensagem)
    this.status = status
    this.detalhes = detalhes
  }
}

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: () => void) { onUnauthorized = fn }

async function parse(res: Response) {
  const ct = res.headers.get('content-type') || ''
  if (res.status === 204) return null
  if (ct.includes('application/json')) return res.json()
  return res.text()
}

async function request<T>(method: string, url: string, body?: unknown, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(init.headers as Record<string, string> | undefined) }
  let payload: BodyInit | undefined
  if (body instanceof FormData) payload = body
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body) }
  const res = await fetch(url, { ...init, method, headers, body: payload, credentials: 'same-origin' })
  if (res.status === 401 && !url.startsWith('/api/auth/')) {
    onUnauthorized?.()
    throw new ApiError(401, 'Sessão expirada. Entre novamente.')
  }
  if (!res.ok) {
    const data = await parse(res).catch(() => null)
    const mensagem = (data && typeof data === 'object' && (data.mensagem || data.erro)) || (typeof data === 'string' && data) || `Erro ${res.status}`
    const detalhes = data && typeof data === 'object' && Array.isArray(data.detalhes) ? data.detalhes : []
    throw new ApiError(res.status, detalhes.length ? detalhes.join(' · ') : mensagem, detalhes)
  }
  return parse(res) as Promise<T>
}

export const http = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body),
  del: <T>(url: string) => request<T>('DELETE', url),
}

/** Monta query string ignorando valores vazios. */
export function qs(params: Record<string, unknown>): string {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '' || v === false) return
    p.set(k, String(v))
  })
  const s = p.toString()
  return s ? `?${s}` : ''
}

/** Abre um download/arquivo autenticado pela sessão numa nova aba. */
export function abrirArquivo(url: string) {
  window.open(url, '_blank', 'noopener')
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { Page } from '../api/types'

// ----------------------------------------------------------------- toasts
interface Toast { id: number; tipo: 'success' | 'danger' | 'warning'; texto: string }
const ToastCtx = createContext<{ sucesso: (t: string) => void; erro: (t: string) => void; aviso: (t: string) => void } | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Toast[]>([])
  const push = useCallback((tipo: Toast['tipo'], texto: string) => {
    const id = Date.now() + Math.random()
    setLista(l => [...l, { id, tipo, texto }])
    setTimeout(() => setLista(l => l.filter(t => t.id !== id)), tipo === 'success' ? 4000 : 7000)
  }, [])
  const value = useMemo(() => ({ sucesso: (t: string) => push('success', t), erro: (t: string) => push('danger', t), aviso: (t: string) => push('warning', t) }), [push])
  return (
    <ToastCtx.Provider value={value}>
      {children}
      <div className="toast-container">
        {lista.map(t => (
          <div key={t.id} className={`toast-modern toast-${t.tipo}`} role="alert">
            <div className="d-flex align-items-start gap-2 p-3">
              <i className={`bi ${t.tipo === 'success' ? 'bi-check-circle-fill text-success' : t.tipo === 'danger' ? 'bi-x-circle-fill text-danger' : 'bi-exclamation-triangle-fill text-warning'}`}></i>
              <div className="toast-desc">{t.texto}</div>
              <button className="btn-close ms-auto" aria-label="Fechar" onClick={() => setLista(l => l.filter(x => x.id !== t.id))}></button>
            </div>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  )
}
export function useToast() {
  const c = useContext(ToastCtx)
  if (!c) throw new Error('useToast fora do ToastProvider')
  return c
}

// ----------------------------------------------------------------- layout helpers
export function PageHeader({ titulo, subtitulo, voltar, acoes, children }: { titulo: ReactNode; subtitulo?: ReactNode; voltar?: string; acoes?: ReactNode; children?: ReactNode }) {
  return (
    <div className="page-header">
      <div className="d-flex align-items-center gap-3">
        {voltar && <Link className="btn btn-secondary btn-icon" to={voltar} title="Voltar"><i className="bi bi-arrow-left"></i></Link>}
        <div>
          <h1 className="page-title">{titulo}</h1>
          {subtitulo && <div className="page-subtitle">{subtitulo}</div>}
        </div>
      </div>
      {(acoes || children) && <div className="d-flex gap-2 flex-wrap">{acoes}{children}</div>}
    </div>
  )
}

export function Card({ children, className = '', style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return <div className={`card card-panel ${className}`} style={{ overflow: 'hidden', ...style }}>{children}</div>
}

export function Section({ icon, titulo, desc, children, semBorda }: { icon: string; titulo: ReactNode; desc?: ReactNode; children?: ReactNode; semBorda?: boolean }) {
  return (
    <div className="form-section" style={semBorda ? { borderBottom: 'none' } : undefined}>
      <div className="form-section-header">
        <div className="form-section-icon"><i className={`bi ${icon}`}></i></div>
        <div>
          <div className="form-section-title">{titulo}</div>
          {desc && <div className="form-section-desc">{desc}</div>}
        </div>
      </div>
      {children}
    </div>
  )
}

export function EmptyState({ icon = 'bi-inbox', titulo, desc, ctaTo, ctaLabel }: { icon?: string; titulo: string; desc?: string; ctaTo?: string; ctaLabel?: string }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon"><i className={`bi ${icon}`}></i></div>
      <div className="empty-state-title">{titulo}</div>
      {desc && <div className="empty-state-desc">{desc}</div>}
      {ctaTo && <Link to={ctaTo} className="btn btn-primary"><i className="bi bi-plus-lg"></i> {ctaLabel ?? 'Cadastrar'}</Link>}
    </div>
  )
}

export function Kpi({ icon, cor = 'primary', label, valor, extra }: { icon: string; cor?: 'primary' | 'success' | 'warning' | 'info' | 'danger'; label: string; valor: ReactNode; extra?: ReactNode }) {
  return (
    <div className="card card-panel h-100">
      <div className="card-body d-flex align-items-start gap-3">
        <div className={`kpi-icon kpi-icon-${cor}`}><i className={`bi ${icon}`}></i></div>
        <div className="flex-grow-1 min-w-0">
          <div className="kpi-label">{label}</div>
          <div className="kpi-value">{valor}</div>
          {extra && <div className="small text-muted">{extra}</div>}
        </div>
      </div>
    </div>
  )
}

export function Badge({ cor, children, className = '' }: { cor: string; children: ReactNode; className?: string }) {
  return <span className={`badge badge-plain ${cor} ${className}`}>{children}</span>
}

export function Carregando() {
  return <div className="text-center text-muted py-5"><div className="spinner-border spinner-border-sm me-2"></div>Carregando...</div>
}

export function Erro({ erro }: { erro: unknown }) {
  const msg = erro instanceof Error ? erro.message : String(erro)
  return <div className="alert alert-danger"><i className="bi bi-exclamation-triangle-fill"></i> {msg}</div>
}

export function Paginacao({ pagina, onPage }: { pagina: Page<unknown>; onPage: (n: number) => void }) {
  if (pagina.totalPages <= 1) return null
  return (
    <nav className="mt-3">
      <ul className="pagination pagination-sm justify-content-center mb-0">
        <li className={`page-item ${pagina.first ? 'disabled' : ''}`}><button className="page-link" onClick={() => onPage(pagina.number - 1)}>&laquo;</button></li>
        <li className="page-item disabled"><span className="page-link">{pagina.number + 1} / {pagina.totalPages}</span></li>
        <li className={`page-item ${pagina.last ? 'disabled' : ''}`}><button className="page-link" onClick={() => onPage(pagina.number + 1)}>&raquo;</button></li>
      </ul>
    </nav>
  )
}

// ----------------------------------------------------------------- forms
export function Campo({ label, obrigatorio, col = 'col-md-6', hint, erro, children }: { label: ReactNode; obrigatorio?: boolean; col?: string; hint?: ReactNode; erro?: string; children: ReactNode }) {
  return (
    <div className={col}>
      <label className={`form-label ${obrigatorio ? 'required' : ''}`}>{label}</label>
      {children}
      {hint && <div className="form-text">{hint}</div>}
      {erro && <div className="invalid-feedback d-block">{erro}</div>}
    </div>
  )
}

export function AcoesForm({ dica, cancelarTo, label = 'Salvar', salvando }: { dica?: ReactNode; cancelarTo: string; label?: string; salvando?: boolean }) {
  return (
    <div className="sticky-actions">
      <span className="actions-hint"><i className="bi bi-info-circle"></i> {dica ?? 'Campos com * são obrigatórios.'}</span>
      <Link className="btn btn-secondary" to={cancelarTo}>Cancelar</Link>
      <button className="btn btn-primary" type="submit" disabled={salvando}>
        {salvando ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-check-lg"></i>} {label}
      </button>
    </div>
  )
}

/** Confirmação simples (substitui window.confirm mantendo o visual). */
export function useConfirmar() {
  return useCallback((mensagem: string) => window.confirm(mensagem), [])
}

/** Hook utilitário para formulários controlados. */
export function useForm<T extends object>(inicial: T) {
  const [valores, setValores] = useState<T>(inicial)
  useEffect(() => { setValores(inicial) /* eslint-disable-line */ }, [JSON.stringify(inicial)]) // eslint-disable-line react-hooks/exhaustive-deps
  const set = (campo: keyof T, valor: unknown) => setValores(v => ({ ...v, [campo]: valor }))
  const bind = (campo: keyof T) => ({
    name: String(campo),
    value: (valores[campo] ?? '') as string | number,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => set(campo, e.target.value),
  })
  const bindNum = (campo: keyof T) => ({
    name: String(campo),
    value: (valores[campo] ?? '') as string | number,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => set(campo, e.target.value === '' ? '' : Number(e.target.value)),
  })
  return { valores, set, bind, bindNum, setValores }
}

export function Filtro({ children, onSubmit, onLimpar }: { children: ReactNode; onSubmit: () => void; onLimpar?: () => void }) {
  return (
    <Card className="mb-4">
      <form className="form-section row g-2 align-items-end" style={{ borderBottom: 'none' }} onSubmit={e => { e.preventDefault(); onSubmit() }}>
        {children}
        <div className="col-md-1 d-flex gap-1">
          <button className="btn btn-sm btn-primary w-100" type="submit" title="Filtrar"><i className="bi bi-funnel"></i></button>
          {onLimpar && <button type="button" className="btn btn-sm btn-secondary" onClick={onLimpar} title="Limpar"><i className="bi bi-x-lg"></i></button>}
        </div>
      </form>
    </Card>
  )
}

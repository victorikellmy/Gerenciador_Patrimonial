import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth, destinoInicial } from '../auth/AuthContext'
import { api } from '../api'
import type { Perfil } from '../api/types'
import { rotulo } from '../utils/format'

interface Item { to: string; icon: string; label: string; perfis?: Perfil[]; exact?: boolean; badge?: number | string; badgeCor?: string; title?: string }
interface Secao { titulo: string; perfis?: Perfil[]; itens: Item[] }

const OPER: Perfil[] = ['ADMINISTRADOR', 'FISCAL']
const LEITURA: Perfil[] = ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE', 'FINANCEIRO']

export default function AppShell() {
  const { usuario, logout, temPerfil } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileAberto, setMobileAberto] = useState(false)
  const [tema, setTema] = useState(document.documentElement.getAttribute('data-theme') || 'light')
  const resumo = useQuery({ queryKey: ['me-resumo'], queryFn: api.auth.resumo, refetchInterval: 60000, enabled: !!usuario })

  useEffect(() => { setMobileAberto(false) }, [location.pathname])

  // O menu nunca rola na horizontal: se o navegador deslocar (foco, trackpad), volta para 0.
  useEffect(() => {
    const alvos = [document.getElementById('appSidebar'), document.querySelector<HTMLElement>('#appSidebar .sidebar-nav')]
      .filter((e): e is HTMLElement => !!e)
    const travar = (e: Event) => { const el = e.currentTarget as HTMLElement; if (el.scrollLeft !== 0) el.scrollLeft = 0 }
    alvos.forEach(el => { el.scrollLeft = 0; el.addEventListener('scroll', travar, { passive: true }) })
    return () => alvos.forEach(el => el.removeEventListener('scroll', travar))
  }, [])

  const alternarTema = () => {
    const next = tema === 'dark' ? 'light' : 'dark'
    document.documentElement.setAttribute('data-theme', next)
    localStorage.setItem('gp-theme', next)
    setTema(next)
  }
  const alternarCollapse = () => {
    const root = document.documentElement
    root.classList.toggle('sidebar-collapsed')
    localStorage.setItem('gp-sidebar', root.classList.contains('sidebar-collapsed') ? 'collapsed' : 'expanded')
  }

  const r = resumo.data
  const lev = r?.levantamentoAberto
  const secoes: Secao[] = [
    { titulo: 'Visão geral', perfis: LEITURA, itens: [{ to: '/', icon: 'bi-speedometer2', label: 'Dashboard', exact: true }] },
    {
      titulo: 'Frota', perfis: ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE', 'MOTORISTA'], itens: [
        { to: '/frota/gestao', icon: 'bi-speedometer2', label: 'Gestão da frota', perfis: ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE'] },
        { to: '/frota', icon: 'bi-truck-front', label: 'Veículos', exact: true },
        { to: '/frota/deslocamentos', icon: 'bi-signpost-2', label: 'Deslocamentos' },
        { to: '/frota/abastecimentos', icon: 'bi-fuel-pump', label: 'Abastecimentos' },
        { to: '/frota/manutencoes', icon: 'bi-wrench-adjustable', label: 'Manutenções' },
        { to: '/frota/cautelas', icon: 'bi-key', label: 'Cautelas' },
      ],
    },
    {
      titulo: 'Diárias', perfis: LEITURA, itens: [
        { to: '/diarias', icon: 'bi-cash-coin', label: 'Solicitações', exact: true, badge: r?.diariasPendentes || undefined, badgeCor: 'text-bg-warning', title: 'Diárias aguardando sua ação' },
        { to: '/diarias/pagas', icon: 'bi-receipt-cutoff', label: 'Diárias pagas' },
        { to: '/diarias/profissionais', icon: 'bi-person-vcard', label: 'Profissionais', perfis: OPER },
        { to: '/diarias/niveis', icon: 'bi-table', label: 'Tabela de valores', perfis: OPER },
      ],
    },
    {
      titulo: 'Operacional', perfis: OPER, itens: [
        { to: '/pendencias', icon: 'bi-inbox', label: 'Pendências', badge: r?.pendenciasPatrimoniamento || undefined, badgeCor: 'text-bg-danger', title: 'Bens a patrimoniar' },
        { to: '/patrimonios', icon: 'bi-box-seam', label: 'Patrimônios' },
        { to: '/lotacoes', icon: 'bi-building', label: 'Lotações' },
        { to: '/responsaveis', icon: 'bi-person-badge', label: 'Responsáveis' },
        { to: lev ? `/levantamentos/${lev.id}` : '/levantamentos', icon: 'bi-clipboard2-check', label: 'Levantamento', badge: lev ? `${lev.total ? Math.round(100 * (lev.total - lev.pendentes) / lev.total) : 0}%` : undefined, badgeCor: 'text-bg-info' },
      ],
    },
    {
      titulo: 'Análises', perfis: LEITURA, itens: [
        { to: '/patrimonios', icon: 'bi-box-seam', label: 'Patrimônios', perfis: ['GERENTE', 'PRESIDENTE', 'FINANCEIRO'] },
        { to: '/relatorios', icon: 'bi-file-earmark-bar-graph', label: 'Relatórios' },
      ],
    },
    {
      titulo: 'Administração', perfis: ['ADMINISTRADOR'], itens: [
        { to: '/importacao', icon: 'bi-upload', label: 'Importação' },
        { to: '/usuarios', icon: 'bi-people', label: 'Usuários' },
        { to: '/auditoria', icon: 'bi-journal-text', label: 'Auditoria' },
      ],
    },
  ]
  const visivel = (p?: Perfil[]) => !p || temPerfil(...p)

  const sair = async () => { await logout(); navigate('/login') }
  const inicial = (usuario?.nomeCompleto || usuario?.login || 'U').substring(0, 1).toUpperCase()

  return (
    <>
      <aside className={`app-sidebar ${mobileAberto ? 'is-open' : ''}`} id="appSidebar" aria-label="Navegação principal">
        <div className="sidebar-brand">
          <NavLink className="sidebar-brand-link" to={destinoInicial(usuario?.perfil)}>
            <img src="/images/logo-color.png" alt="Fundação Pró-Tocantins" className="sidebar-brand-logo sidebar-brand-logo-light" />
            <img src="/images/logo-white.png" alt="" className="sidebar-brand-logo sidebar-brand-logo-dark" />
            <span className="sidebar-brand-mark-collapsed" aria-hidden="true"><i className="bi bi-box-seam"></i></span>
            <span className="sidebar-brand-text"><span className="sidebar-brand-title">Patrimônio</span></span>
          </NavLink>
          <button type="button" className="sidebar-collapse-btn d-none d-lg-inline-flex" onClick={alternarCollapse} title="Recolher menu"><i className="bi bi-chevron-double-left"></i></button>
        </div>
        <nav className="sidebar-nav">
          {secoes.filter(s => visivel(s.perfis)).map(s => (
            <div className="sidebar-section" key={s.titulo}>
              <div className="sidebar-section-title">{s.titulo}</div>
              {s.itens.filter(i => visivel(i.perfis)).map(i => (
                <NavLink key={i.to + i.label} to={i.to} end={i.exact} className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`} title={i.title}>
                  <i className={`bi ${i.icon}`}></i>
                  <span className="sidebar-link-label">{i.label}</span>
                  {i.badge !== undefined && <span className={`badge rounded-pill ms-auto badge-plain ${i.badgeCor ?? 'text-bg-secondary'}`}>{i.badge}</span>}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span className="nav-avatar">{inicial}</span>
            <div className="sidebar-user-info">
              <span className="sidebar-user-name">{usuario?.nomeCompleto}</span>
              <span className="sidebar-user-role">{rotulo(usuario?.perfil)}</span>
            </div>
          </div>
        </div>
      </aside>
      <div className={`sidebar-backdrop ${mobileAberto ? 'is-visible' : ''}`} onClick={() => setMobileAberto(false)} aria-hidden="true"></div>

      <header className="app-topbar">
        <button type="button" className="topbar-icon-btn d-lg-none" onClick={() => setMobileAberto(true)} aria-label="Abrir menu"><i className="bi bi-list"></i></button>
        <button type="button" className="topbar-icon-btn d-none d-lg-inline-flex" onClick={alternarCollapse} aria-label="Alternar menu"><i className="bi bi-layout-sidebar"></i></button>
        <div className="topbar-breadcrumb" id="topbarBreadcrumb"></div>
        <div className="topbar-spacer"></div>
        {temPerfil('ADMINISTRADOR', 'FISCAL') && (
          <NavLink className="topbar-icon-btn position-relative text-decoration-none" to="/pendencias" title={r?.pendenciasPatrimoniamento ? `${r.pendenciasPatrimoniamento} bem(ns) a patrimoniar` : 'Nenhuma pendência'}>
            <i className={`bi ${r?.pendenciasPatrimoniamento ? 'bi-bell-fill' : 'bi-bell'}`}></i>
            {!!r?.pendenciasPatrimoniamento && <span className="position-absolute top-0 start-100 translate-middle badge rounded-pill text-bg-danger badge-plain" style={{ fontSize: '.65rem', marginLeft: -6, marginTop: 6 }}>{r.pendenciasPatrimoniamento}</span>}
          </NavLink>
        )}
        <button type="button" className="topbar-icon-btn theme-toggle" onClick={alternarTema} title="Alternar tema claro/escuro">
          <i className={`bi ${tema === 'dark' ? 'bi-sun' : 'bi-moon-stars'}`}></i>
        </button>
        <div className="topbar-divider d-none d-md-block"></div>
        <div className="dropdown">
          <button type="button" className="topbar-user-btn dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
            <span className="nav-avatar">{inicial}</span>
            <span className="d-none d-md-flex flex-column text-start lh-sm">
              <span className="small fw-semibold">{usuario?.login}</span>
              <span className="topbar-user-role">{rotulo(usuario?.perfil)}</span>
            </span>
          </button>
          <ul className="dropdown-menu dropdown-menu-end">
            <li className="px-3 py-2 border-bottom"><div className="small fw-semibold">{usuario?.nomeCompleto}</div><div className="small text-muted">{rotulo(usuario?.perfil)}</div></li>
            <li><NavLink className="dropdown-item" to="/minha-conta/senha"><i className="bi bi-key"></i> Trocar senha</NavLink></li>
            <li><hr className="dropdown-divider" /></li>
            <li><button className="dropdown-item text-danger" onClick={sair}><i className="bi bi-box-arrow-right"></i> Sair</button></li>
          </ul>
        </div>
      </header>

      <main className="container-fluid pb-5">
        <Outlet />
      </main>
    </>
  )
}

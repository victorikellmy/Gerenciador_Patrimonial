import { useState, type FormEvent } from 'react'
import { NavLink } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import type { Perfil, UsuarioDominio } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, PageHeader, Section, useToast } from '../components/ui'
import { rotulo } from '../utils/format'

/** Perfis que um grupo do AD pode dar; "Usuário padrão" = nenhum grupo (só entra, sem módulos). */
const ACESSOS: Perfil[] = ['USUARIO', 'ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE', 'FINANCEIRO', 'MOTORISTA']
const DESC: Partial<Record<Perfil, string>> = {
  USUARIO: 'Entra com a conta da rede, mas não vê nenhum módulo.',
  ADMINISTRADOR: 'Acesso total, incluindo usuários, grupos e auditoria.',
  FISCAL: 'Opera patrimônio, levantamentos, frota e diárias.',
  GERENTE: 'Somente leitura: painel da frota, patrimônios e relatórios.',
  PRESIDENTE: 'Defere/indefere diárias e edita a frota.',
  FINANCEIRO: 'Registra pagamento de diárias.',
  MOTORISTA: 'Somente frota.',
}

/** Abas da área de usuários: contas do sistema × grupos da rede (AD). */
export function AbasUsuarios() {
  return (
    <ul className="nav nav-tabs border-0 mb-3">
      <li className="nav-item"><NavLink end to="/usuarios" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><i className="bi bi-people me-1"></i> Contas no sistema</NavLink></li>
      <li className="nav-item"><NavLink to="/usuarios/rede" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}><i className="bi bi-diagram-3 me-1"></i> Grupos da rede (AD)</NavLink></li>
    </ul>
  )
}

export default function UsuariosRede() {
  const qc = useQueryClient()
  const toast = useToast()
  const { usuario } = useAuth()
  const status = useQuery({ queryKey: ['dominio-status'], queryFn: api.dominio.status })
  const [termo, setTermo] = useState('')
  const [busca, setBusca] = useState('')
  const pronto = !!status.data?.habilitado && !!status.data?.conectado
  const contas = useQuery({ queryKey: ['dominio-usuarios', busca], queryFn: () => api.dominio.usuarios(busca), enabled: pronto })
  const [edicao, setEdicao] = useState<Record<string, { perfil: Perfil; podeAbastecer: boolean }>>({})
  const [salvando, setSalvando] = useState<string | null>(null)
  const [criando, setCriando] = useState(false)

  const faltando = status.data?.grupos.filter(g => !g.existe) ?? []

  const criarGrupos = async () => {
    if (!confirm(`Criar no AD os grupos que faltam (${faltando.map(g => g.nome).join(', ')})?`)) return
    setCriando(true)
    try { qc.setQueryData(['dominio-status'], await api.dominio.criarGrupos()); toast.sucesso('Grupos criados no AD.') }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao criar os grupos.') } finally { setCriando(false) }
  }

  const valor = (c: UsuarioDominio) => edicao[c.login] ?? { perfil: c.perfil, podeAbastecer: c.podeAbastecer }
  const alterado = (c: UsuarioDominio) => { const v = valor(c); return v.perfil !== c.perfil || v.podeAbastecer !== c.podeAbastecer }
  const editar = (c: UsuarioDominio, mud: Partial<{ perfil: Perfil; podeAbastecer: boolean }>) =>
    setEdicao(e => ({ ...e, [c.login]: { ...valor(c), ...mud } }))

  const salvar = async (c: UsuarioDominio) => {
    const v = valor(c)
    if (v.perfil === 'ADMINISTRADOR' && c.perfil !== 'ADMINISTRADOR' && !confirm(`Dar acesso TOTAL (Administrador) a ${c.nome}?`)) return
    setSalvando(c.login)
    try {
      const atualizado = await api.dominio.definirAcesso(c.login, v.perfil === 'USUARIO' ? null : v.perfil, v.podeAbastecer)
      qc.setQueryData<UsuarioDominio[]>(['dominio-usuarios', busca], lista => lista?.map(x => x.login === c.login ? atualizado : x))
      setEdicao(e => { const n = { ...e }; delete n[c.login]; return n })
      qc.invalidateQueries({ queryKey: ['dominio-status'] })
      qc.invalidateQueries({ queryKey: ['usuarios'] })
      toast.sucesso(`${c.nome}: ${rotulo(atualizado.perfil)} gravado no AD. Quem estava conectado com essa conta precisa entrar de novo.`)
    } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao gravar no AD.') } finally { setSalvando(null) }
  }

  const pesquisar = (e: FormEvent) => { e.preventDefault(); setBusca(termo.trim()) }

  return (
    <>
      <PageHeader titulo="Usuários" subtitulo="Quem tem conta na rede da Fundação entra com o mesmo usuário e senha do Windows. O acesso de cada um é o grupo do Patrimônio no AD." />
      <AbasUsuarios />

      <Card className="mb-3">
        <Section icon="bi-hdd-network" titulo="Conexão com o AD" semBorda
          desc={status.data ? <>Domínio <strong>{status.data.dominio}</strong> · {status.data.servidores.join(', ')} · grupos em <code>{status.data.ouGrupos}</code></> : undefined}>
          {status.isLoading ? <Carregando /> : status.error ? <Erro erro={status.error} /> : !status.data!.habilitado ? (
            <div className="alert alert-secondary mb-0"><i className="bi bi-toggle-off"></i> O login pela rede está desligado neste servidor. Defina <code>AD_HABILITADO=true</code> e a conta de serviço (veja <code>docs/INTEGRACAO_AD.md</code>).</div>
          ) : !status.data!.conectado ? (
            <div className="alert alert-danger mb-0 d-flex align-items-center gap-2"><i className="bi bi-exclamation-octagon"></i><div className="me-auto">{status.data!.erro}</div><button className="btn btn-sm btn-outline-danger" onClick={() => status.refetch()}>Tentar de novo</button></div>
          ) : (
            <>
              <div className="d-flex flex-wrap gap-2 mb-2">
                {status.data!.grupos.map(g => (
                  <span key={g.nome} className={`badge badge-plain ${g.existe ? 'text-bg-light' : 'text-bg-warning'}`} title={g.descricao}>
                    <i className={`bi ${g.existe ? 'bi-people' : 'bi-exclamation-triangle'} me-1`}></i>{g.nome}{g.existe ? ` · ${g.membros}` : ' · não existe'}
                  </span>
                ))}
              </div>
              {faltando.length > 0 && (
                <div className="alert alert-warning mb-0 d-flex align-items-center gap-2">
                  <i className="bi bi-exclamation-triangle"></i>
                  <div className="me-auto">Faltam {faltando.length} grupo(s) no AD. Sem eles não dá para dar esse acesso.</div>
                  <button className="btn btn-sm btn-warning" disabled={criando} onClick={criarGrupos}>{criando ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-plus-lg"></i>} Criar grupos que faltam</button>
                </div>
              )}
            </>
          )}
        </Section>
      </Card>

      {pronto && (
        <Card>
          <Section icon="bi-person-lines-fill" titulo="Contas da rede" desc="Escolha o acesso e clique em Gravar: o sistema inclui a pessoa no grupo certo do AD e tira dos outros grupos do Patrimônio.">
            <form className="d-flex gap-2 mb-3" onSubmit={pesquisar} style={{ maxWidth: 520 }}>
              <input className="form-control" placeholder="Nome ou login (vazio lista todos)" value={termo} onChange={e => setTermo(e.target.value)} />
              <button className="btn btn-secondary"><i className="bi bi-search"></i> Buscar</button>
            </form>
            {contas.isLoading ? <Carregando /> : contas.error ? <Erro erro={contas.error} /> : !contas.data?.length ? <EmptyState icon="bi-person-x" titulo="Nenhuma conta encontrada no AD" /> : (
              <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0 align-middle">
                <thead><tr><th>Nome</th><th>Login</th><th>Na rede</th><th style={{ minWidth: 190 }}>Acesso no Patrimônio</th><th>Abastece</th><th></th></tr></thead>
                <tbody>{contas.data.map(c => {
                  const v = valor(c)
                  const proprio = c.login === usuario?.login
                  return (
                    <tr key={c.login} className={c.ativoNoDominio ? '' : 'text-muted'}>
                      <td><strong>{c.nome}</strong>{c.email && <div className="small text-muted">{c.email}</div>}{c.ativoNoSistema === false && <span className="badge badge-plain text-bg-secondary ms-1" title="Desativado só no Patrimônio (Contas no sistema)">inativo no sistema</span>}</td>
                      <td><code>{c.login}</code></td>
                      <td><span className={`badge badge-plain ${c.ativoNoDominio ? 'text-bg-success' : 'text-bg-secondary'}`}>{c.ativoNoDominio ? 'Ativa' : 'Desativada'}</span></td>
                      <td>
                        <select className="form-select form-select-sm" value={v.perfil} disabled={proprio || salvando === c.login} title={proprio ? 'Você não pode alterar o seu próprio acesso.' : DESC[v.perfil]}
                          onChange={e => editar(c, { perfil: e.target.value as Perfil })}>
                          {ACESSOS.map(p => <option key={p} value={p}>{rotulo(p)}</option>)}
                        </select>
                      </td>
                      <td><div className="form-check form-switch mb-0" title="Grupo extra: registra abastecimentos da frota com nota fiscal">
                        <input className="form-check-input" type="checkbox" checked={v.podeAbastecer} disabled={proprio || salvando === c.login} onChange={e => editar(c, { podeAbastecer: e.target.checked })} />
                      </div></td>
                      <td className="text-end text-nowrap">
                        {alterado(c) && <>
                          <button className="btn btn-sm btn-link" onClick={() => setEdicao(e => { const n = { ...e }; delete n[c.login]; return n })}>Desfazer</button>
                          <button className="btn btn-sm btn-primary" disabled={salvando === c.login} onClick={() => salvar(c)}>{salvando === c.login ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-cloud-arrow-up"></i>} Gravar no AD</button>
                        </>}
                      </td>
                    </tr>
                  )
                })}</tbody>
              </table></div>
            )}
            {contas.data && contas.data.length >= 300 && <div className="form-text mt-2">Mostrando as primeiras 300 contas: refine a busca.</div>}
          </Section>
        </Card>
      )}
    </>
  )
}

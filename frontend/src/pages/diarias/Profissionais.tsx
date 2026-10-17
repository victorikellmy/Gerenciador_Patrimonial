import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { Card, Carregando, EmptyState, Erro, PageHeader, useToast } from '../../components/ui'
import { moeda } from '../../utils/format'

export default function Profissionais() {
  const qc = useQueryClient()
  const toast = useToast()
  const [busca, setBusca] = useState('')
  const [somenteAtivos, setSomenteAtivos] = useState(true)
  const q = useQuery({ queryKey: ['profissionais', busca, somenteAtivos], queryFn: () => api.diarias.profissionais(busca || undefined, somenteAtivos) })
  const alternar = async (id: number, ativo: boolean) => { try { await api.diarias.ativoProfissional(id, ativo); toast.sucesso(ativo ? 'Profissional reativado.' : 'Profissional inativado.'); qc.invalidateQueries({ queryKey: ['profissionais'] }) } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  return (
    <>
      <PageHeader titulo="Profissionais" subtitulo="Servidores e colaboradores que recebem diárias." acoes={<Link className="btn btn-primary" to="/diarias/profissionais/novo"><i className="bi bi-plus-lg"></i> Novo profissional</Link>} />
      <Card>
        <div className="p-3 border-bottom d-flex flex-wrap gap-2 align-items-center">
          <input className="form-control form-control-sm" style={{ maxWidth: 300 }} placeholder="Buscar por nome, matrícula ou CPF" value={busca} onChange={e => setBusca(e.target.value)} />
          <div className="form-check form-switch ms-auto mb-0"><input className="form-check-input" type="checkbox" id="ativos" checked={somenteAtivos} onChange={e => setSomenteAtivos(e.target.checked)} /><label className="form-check-label small" htmlFor="ativos">Somente ativos</label></div>
        </div>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : !q.data?.length ? <EmptyState icon="bi-person-vcard" titulo="Nenhum profissional" ctaTo="/diarias/profissionais/novo" ctaLabel="Novo profissional" /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Nome</th><th>Matrícula</th><th>Lotação</th><th>Cargo / nível</th><th className="text-end">Capitais</th><th className="text-end">Interior TO</th><th className="text-end">Outro estado</th><th>CPF</th><th>Situação</th><th></th></tr></thead>
            <tbody>{q.data.map(p => <tr key={p.id} className={p.ativo ? '' : 'text-muted'}>
              <td><strong>{p.nome}</strong></td><td>{p.matricula || '—'}</td><td>{p.lotacao || '—'}</td><td>{p.nivelDescricao}</td>
              <td className="text-end">{moeda(p.valorCapitais)}</td><td className="text-end">{moeda(p.valorInteriorEstado)}</td><td className="text-end">{moeda(p.valorInteriorOutroEstado)}</td>
              <td>{p.cpf}</td><td><span className={`badge badge-plain ${p.ativo ? 'text-bg-success' : 'text-bg-secondary'}`}>{p.ativo ? 'Ativo' : 'Inativo'}</span></td>
              <td className="text-end text-nowrap"><Link className="btn btn-sm btn-secondary btn-icon" to={`/diarias/profissionais/${p.id}/editar`} title="Editar"><i className="bi bi-pencil"></i></Link>{' '}<button className={`btn btn-sm btn-icon ${p.ativo ? 'btn-outline-danger' : 'btn-outline-success'}`} title={p.ativo ? 'Inativar' : 'Reativar'} onClick={() => alternar(p.id, !p.ativo)}><i className={`bi ${p.ativo ? 'bi-person-x' : 'bi-person-check'}`}></i></button></td>
            </tr>)}</tbody>
          </table></div>)}
      </Card>
    </>
  )
}

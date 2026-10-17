import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { dataHora } from '../../utils/format'

export default function LevantamentosLista() {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const q = useQuery({ queryKey: ['levantamentos'], queryFn: api.levantamentos.listar })
  const [abrindo, setAbrindo] = useState(false)
  const [form, setForm] = useState({ ano: new Date().getFullYear(), descricao: '', observacao: '' })
  const [salvando, setSalvando] = useState(false)
  const temAberto = q.data?.some(l => l.status === 'ABERTO')

  const abrir = async (e: FormEvent) => {
    e.preventDefault(); setSalvando(true)
    try { await api.levantamentos.abrir(form.ano, form.descricao, form.observacao); toast.sucesso('Levantamento aberto.'); setAbrindo(false); qc.invalidateQueries({ queryKey: ['levantamentos'] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }) }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao abrir.') } finally { setSalvando(false) }
  }
  return (
    <>
      <PageHeader titulo="Levantamento patrimonial" subtitulo="Inventário físico anual por local, com checklist e foto." acoes={temPerfil('ADMINISTRADOR') && !temAberto && <button className="btn btn-primary" onClick={() => setAbrindo(true)}><i className="bi bi-plus-lg"></i> Abrir levantamento</button>} />
      {abrindo && <Card className="mb-3"><form onSubmit={abrir}><Section icon="bi-clipboard2-check" titulo="Abrir novo levantamento" desc="Cria um item de conferência para cada bem ativo, no local onde está lotado hoje." semBorda>
        <div className="row g-3">
          <div className="col-md-2"><label className="form-label required">Ano</label><input type="number" className="form-control" required value={form.ano} onChange={e => setForm({ ...form, ano: Number(e.target.value) })} /></div>
          <div className="col-md-5"><label className="form-label">Descrição</label><input className="form-control" value={form.descricao} onChange={e => setForm({ ...form, descricao: e.target.value })} placeholder={`Levantamento ${form.ano}`} /></div>
          <div className="col-md-5"><label className="form-label">Observação</label><input className="form-control" value={form.observacao} onChange={e => setForm({ ...form, observacao: e.target.value })} /></div>
          <div className="col-12 d-flex justify-content-end gap-2"><button type="button" className="btn btn-secondary" onClick={() => setAbrindo(false)}>Cancelar</button><button className="btn btn-primary" disabled={salvando}>Abrir</button></div>
        </div></Section></form></Card>}
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : !q.data?.length ? <EmptyState icon="bi-clipboard2-check" titulo="Nenhum levantamento" desc="Abra o levantamento do ano para iniciar a conferência." /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Ano</th><th>Descrição</th><th>Status</th><th>Progresso</th><th className="text-center">OK</th><th className="text-center">Avariados</th><th className="text-center">Não localizados</th><th>Aberto</th><th>Concluído</th><th></th></tr></thead>
            <tbody>{q.data.map(l => { const pct = l.total ? Math.round(100 * (l.total - l.pendentes) / l.total) : 0; return (
              <tr key={l.id}>
                <td><strong>{l.ano}</strong></td><td>{l.descricao}</td>
                <td><span className={`badge badge-plain ${l.status === 'ABERTO' ? 'text-bg-info' : 'text-bg-success'}`}>{l.status === 'ABERTO' ? 'Em andamento' : 'Concluído'}</span></td>
                <td style={{ minWidth: 160 }}><div className="d-flex align-items-center gap-2"><div className="progress flex-grow-1" style={{ height: 8 }}><div className="progress-bar" style={{ width: `${pct}%` }}></div></div><span className="small">{pct}%</span></div><div className="small text-muted">{l.total - l.pendentes}/{l.total} conferidos</div></td>
                <td className="text-center text-success">{l.ok}</td><td className="text-center text-warning">{l.avariados}</td><td className="text-center text-danger">{l.naoLocalizados}</td>
                <td className="small">{l.abertoPor}<br />{dataHora(l.abertoEm)}</td><td className="small">{l.concluidoPor ?? '—'}<br />{l.concluidoEm ? dataHora(l.concluidoEm) : ''}</td>
                <td className="text-end"><Link className="btn btn-sm btn-primary" to={`/levantamentos/${l.id}`}>{l.status === 'ABERTO' ? 'Conferir' : 'Ver'}</Link></td>
              </tr>) })}</tbody>
          </table></div>)}
      </Card>
    </>
  )
}

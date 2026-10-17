import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import type { Responsavel } from '../api/types'
import { abrirArquivo } from '../api/http'
import { Card, Carregando, EmptyState, Erro, PageHeader, Paginacao, Section, useToast } from '../components/ui'

const vazio = { nomeCompleto: '', matricula: '', cidade: '', lotacaoId: '' as number | '' }

export default function Responsaveis() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [busca, setBusca] = useState('')
  const [form, setForm] = useState<typeof vazio & { id?: number } | null>(null)
  const [salvando, setSalvando] = useState(false)
  const q = useQuery({ queryKey: ['responsaveis', page], queryFn: () => api.responsaveis.listar(page, 30) })
  const lotacoes = useQuery({ queryKey: ['lotacoes-todas'], queryFn: api.lotacoes.todas })
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['responsaveis'] }); qc.invalidateQueries({ queryKey: ['responsaveis-todos'] }) }

  const salvar = async (e: FormEvent) => {
    e.preventDefault(); if (!form) return; setSalvando(true)
    try {
      const req = { ...form, lotacaoId: form.lotacaoId || undefined } as Partial<Responsavel>
      if (form.id) await api.responsaveis.atualizar(form.id, req); else await api.responsaveis.criar(req)
      toast.sucesso('Responsável salvo.'); setForm(null); invalidar()
    } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  const inativar = async (r: Responsavel) => { if (!confirm(`Inativar ${r.nomeCompleto}?`)) return; try { await api.responsaveis.inativar(r.id); toast.sucesso('Responsável inativado.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  const lista = (q.data?.content ?? []).filter(r => !busca || `${r.nomeCompleto} ${r.matricula ?? ''} ${r.lotacaoNome ?? ''}`.toLowerCase().includes(busca.toLowerCase()))

  return (
    <>
      <PageHeader titulo="Responsáveis" subtitulo="Pessoas que respondem pelos bens de cada lotação." acoes={<button className="btn btn-primary" onClick={() => setForm({ ...vazio })}><i className="bi bi-plus-lg"></i> Novo responsável</button>} />
      {form && <Card className="mb-3"><form onSubmit={salvar}><Section icon="bi-person-badge" titulo={form.id ? 'Editar responsável' : 'Novo responsável'} semBorda>
        <div className="row g-3">
          <div className="col-md-5"><label className="form-label required">Nome completo</label><input className="form-control" required value={form.nomeCompleto} onChange={e => setForm({ ...form, nomeCompleto: e.target.value })} /></div>
          <div className="col-md-2"><label className="form-label">Matrícula</label><input className="form-control" value={form.matricula} onChange={e => setForm({ ...form, matricula: e.target.value })} /></div>
          <div className="col-md-2"><label className="form-label">Cidade</label><input className="form-control" value={form.cidade} onChange={e => setForm({ ...form, cidade: e.target.value })} /></div>
          <div className="col-md-3"><label className="form-label">Lotação</label><select className="form-select" value={form.lotacaoId} onChange={e => setForm({ ...form, lotacaoId: e.target.value ? Number(e.target.value) : '' })}><option value="">—</option>{lotacoes.data?.map(l => <option key={l.id} value={l.id}>{l.upm} · {l.nome}</option>)}</select></div>
          <div className="col-12 d-flex justify-content-end gap-2"><button type="button" className="btn btn-secondary" onClick={() => setForm(null)}>Cancelar</button><button className="btn btn-primary" disabled={salvando}>Salvar</button></div>
        </div></Section></form></Card>}
      <Card>
        <div className="p-3 border-bottom"><input className="form-control form-control-sm" placeholder="Buscar nesta página..." value={busca} onChange={e => setBusca(e.target.value)} /></div>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : !lista.length ? <EmptyState icon="bi-person-badge" titulo="Nenhum responsável" /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Nome</th><th>Matrícula</th><th>Cidade</th><th>Lotação</th><th>Situação</th><th></th></tr></thead>
            <tbody>{lista.map(r => <tr key={r.id} className={r.ativo ? '' : 'text-muted'}><td><strong>{r.nomeCompleto}</strong></td><td>{r.matricula || '—'}</td><td>{r.cidade || '—'}</td><td>{r.lotacaoNome || '—'}</td><td><span className={`badge badge-plain ${r.ativo ? 'text-bg-success' : 'text-bg-secondary'}`}>{r.ativo ? 'Ativo' : 'Inativo'}</span></td>
              <td className="text-end text-nowrap">
                <button className="btn btn-sm btn-secondary btn-icon" title="Termo de responsabilidade" onClick={() => abrirArquivo(api.relatorios.termoUrl(r.id))}><i className="bi bi-file-earmark-pdf"></i></button>{' '}
                <button className="btn btn-sm btn-secondary btn-icon" onClick={() => setForm({ id: r.id, nomeCompleto: r.nomeCompleto, matricula: r.matricula ?? '', cidade: r.cidade ?? '', lotacaoId: r.lotacaoId ?? '' })} title="Editar"><i className="bi bi-pencil"></i></button>{' '}
                {r.ativo && <button className="btn btn-sm btn-outline-danger btn-icon" onClick={() => inativar(r)} title="Inativar"><i className="bi bi-person-x"></i></button>}
              </td></tr>)}</tbody>
          </table></div>)}
        {q.data && <div className="px-3 pb-3"><Paginacao pagina={q.data} onPage={setPage} /></div>}
      </Card>
    </>
  )
}

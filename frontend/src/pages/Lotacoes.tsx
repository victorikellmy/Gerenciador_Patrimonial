import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import type { Lotacao } from '../api/types'
import { Card, Carregando, EmptyState, Erro, PageHeader, Paginacao, Section, useToast } from '../components/ui'

const vazio = { upm: '', nome: '', cidade: '', tipoLocal: 'INTERNO', responsavelAtualId: '' as number | '' }

export default function Lotacoes() {
  const qc = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [busca, setBusca] = useState('')
  const [form, setForm] = useState<typeof vazio & { id?: number } | null>(null)
  const [salvando, setSalvando] = useState(false)
  const q = useQuery({ queryKey: ['lotacoes', page], queryFn: () => api.lotacoes.listar(page, 30) })
  const responsaveis = useQuery({ queryKey: ['responsaveis-todos'], queryFn: api.responsaveis.todos })
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['lotacoes'] }); qc.invalidateQueries({ queryKey: ['lotacoes-todas'] }) }

  const salvar = async (e: FormEvent) => {
    e.preventDefault(); if (!form) return; setSalvando(true)
    try {
      const req = { ...form, responsavelAtualId: form.responsavelAtualId || undefined } as Partial<Lotacao>
      if (form.id) await api.lotacoes.atualizar(form.id, req); else await api.lotacoes.criar(req)
      toast.sucesso('Lotação salva.'); setForm(null); invalidar()
    } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  const excluir = async (l: Lotacao) => { if (!confirm(`Excluir a lotação ${l.upm} · ${l.nome}?`)) return; try { await api.lotacoes.excluir(l.id); toast.sucesso('Lotação excluída.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao excluir.') } }
  const editar = (l: Lotacao) => setForm({ id: l.id, upm: l.upm, nome: l.nome, cidade: l.cidade ?? '', tipoLocal: l.tipoLocal, responsavelAtualId: l.responsavelAtualId ?? '' })
  const lista = (q.data?.content ?? []).filter(l => !busca || `${l.upm} ${l.nome} ${l.cidade ?? ''} ${l.responsavelAtualNome ?? ''}`.toLowerCase().includes(busca.toLowerCase()))

  return (
    <>
      <PageHeader titulo="Lotações" subtitulo="Salas, setores e unidades onde os bens ficam alocados." acoes={<button className="btn btn-primary" onClick={() => setForm({ ...vazio })}><i className="bi bi-plus-lg"></i> Nova lotação</button>} />
      {form && <Card className="mb-3"><form onSubmit={salvar}><Section icon="bi-building" titulo={form.id ? 'Editar lotação' : 'Nova lotação'} semBorda>
        <div className="row g-3">
          <div className="col-md-2"><label className="form-label required">UPM</label><input className="form-control" required value={form.upm} onChange={e => setForm({ ...form, upm: e.target.value })} placeholder="Ex: 1 BPM" /></div>
          <div className="col-md-4"><label className="form-label required">Nome / sala</label><input className="form-control" required value={form.nome} onChange={e => setForm({ ...form, nome: e.target.value })} /></div>
          <div className="col-md-2"><label className="form-label">Cidade</label><input className="form-control" value={form.cidade} onChange={e => setForm({ ...form, cidade: e.target.value })} /></div>
          <div className="col-md-2"><label className="form-label">Tipo</label><select className="form-select" value={form.tipoLocal} onChange={e => setForm({ ...form, tipoLocal: e.target.value })}><option value="INTERNO">Interno</option><option value="EXTERNO">Externo</option></select></div>
          <div className="col-md-2"><label className="form-label">Responsável</label><select className="form-select" value={form.responsavelAtualId} onChange={e => setForm({ ...form, responsavelAtualId: e.target.value ? Number(e.target.value) : '' })}><option value="">—</option>{responsaveis.data?.map(r => <option key={r.id} value={r.id}>{r.nomeCompleto}</option>)}</select></div>
          <div className="col-12 d-flex justify-content-end gap-2"><button type="button" className="btn btn-secondary" onClick={() => setForm(null)}>Cancelar</button><button className="btn btn-primary" disabled={salvando}>Salvar</button></div>
        </div></Section></form></Card>}
      <Card>
        <div className="p-3 border-bottom"><input className="form-control form-control-sm" placeholder="Buscar nesta página..." value={busca} onChange={e => setBusca(e.target.value)} /></div>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : !lista.length ? <EmptyState icon="bi-building" titulo="Nenhuma lotação" /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>UPM</th><th>Nome</th><th>Cidade</th><th>Tipo</th><th>Responsável atual</th><th></th></tr></thead>
            <tbody>{lista.map(l => <tr key={l.id}><td><strong>{l.upm}</strong></td><td>{l.nome}</td><td>{l.cidade || '—'}</td><td><span className="badge badge-plain text-bg-light">{l.tipoLocal === 'INTERNO' ? 'Interno' : 'Externo'}</span></td><td>{l.responsavelAtualNome || '—'}</td>
              <td className="text-end text-nowrap"><button className="btn btn-sm btn-secondary btn-icon" onClick={() => editar(l)} title="Editar"><i className="bi bi-pencil"></i></button> <button className="btn btn-sm btn-outline-danger btn-icon" onClick={() => excluir(l)} title="Excluir"><i className="bi bi-trash"></i></button></td></tr>)}</tbody>
          </table></div>)}
        {q.data && <div className="px-3 pb-3"><Paginacao pagina={q.data} onPage={setPage} /></div>}
      </Card>
    </>
  )
}

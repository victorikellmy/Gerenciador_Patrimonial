import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { Card, Carregando, EmptyState, Erro, Filtro, PageHeader, Paginacao } from '../components/ui'
import { dataHora } from '../utils/format'

const ACOES = ['CREATE', 'UPDATE', 'DELETE', 'MOVIMENTAR', 'BAIXAR', 'ANEXAR', 'REMOVER_ANEXO', 'LOGIN', 'RECEBER_INTEGRACAO', 'PATRIMONIAR', 'LEVANTAMENTO', 'FROTA']
const cor: Record<string, string> = { CREATE: 'text-bg-success', UPDATE: 'text-bg-info', DELETE: 'text-bg-danger', BAIXAR: 'text-bg-danger', MOVIMENTAR: 'text-bg-warning', LOGIN: 'text-bg-light' }

export default function Auditoria() {
  const [f, setF] = useState({ usuario: '', acao: '', entidade: '', entidadeId: '', de: '', ate: '' })
  const [aplicado, setAplicado] = useState(f)
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['auditoria', aplicado, page], queryFn: () => api.auditoria.buscar({ ...aplicado, de: aplicado.de ? `${aplicado.de}T00:00:00` : '', ate: aplicado.ate ? `${aplicado.ate}T23:59:59` : '' }, page) })
  return (
    <>
      <PageHeader titulo="Auditoria" subtitulo="Registro de todas as ações realizadas no sistema." />
      <Filtro onSubmit={() => { setAplicado(f); setPage(0) }} onLimpar={() => { const v = { usuario: '', acao: '', entidade: '', entidadeId: '', de: '', ate: '' }; setF(v); setAplicado(v) }}>
        <div className="col-md-2"><label className="form-label small">Usuário</label><input className="form-control form-control-sm" value={f.usuario} onChange={e => setF({ ...f, usuario: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Ação</label><select className="form-select form-select-sm" value={f.acao} onChange={e => setF({ ...f, acao: e.target.value })}><option value="">Todas</option>{ACOES.map(a => <option key={a}>{a}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">Entidade</label><input className="form-control form-control-sm" placeholder="Patrimonio, Veiculo..." value={f.entidade} onChange={e => setF({ ...f, entidade: e.target.value })} /></div>
        <div className="col-md-1"><label className="form-label small">ID</label><input type="number" className="form-control form-control-sm" value={f.entidadeId} onChange={e => setF({ ...f, entidadeId: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">De</label><input type="date" className="form-control form-control-sm" value={f.de} onChange={e => setF({ ...f, de: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Até</label><input type="date" className="form-control form-control-sm" value={f.ate} onChange={e => setF({ ...f, ate: e.target.value })} /></div>
      </Filtro>
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-journal-text" titulo="Nenhum registro" /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Data/hora</th><th>Usuário</th><th>Ação</th><th>Entidade</th><th>Descrição</th><th>IP</th></tr></thead>
              <tbody>{q.data!.content.map(a => <tr key={a.id}><td className="text-nowrap">{dataHora(a.dataHora)}</td><td>{a.usuario}</td><td><span className={`badge badge-plain ${cor[a.acao] ?? 'text-bg-secondary'}`}>{a.acao}</span></td><td>{a.entidade}{a.entidadeId ? ` #${a.entidadeId}` : ''}</td><td className="texto-limitado" title={a.descricao}>{a.descricao}</td><td className="small text-muted">{a.ipOrigem}</td></tr>)}</tbody>
            </table></div>
            <div className="px-3 pb-3"><div className="small text-muted mt-2">{q.data!.totalElements} registro(s)</div><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

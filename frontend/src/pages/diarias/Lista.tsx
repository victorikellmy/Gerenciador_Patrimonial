import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api'
import type { StatusDiaria } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, Carregando, EmptyState, Erro, Filtro, PageHeader, Paginacao } from '../../components/ui'
import { badgeStatusDiaria, data, moeda, numero, rotulo } from '../../utils/format'

const STATUS: StatusDiaria[] = ['SOLICITADO', 'CONFIRMADO', 'DEFERIDO', 'INDEFERIDO', 'PAGO']

export default function DiariasLista() {
  const { usuario, temPerfil } = useAuth()
  const padrao: StatusDiaria | '' = usuario?.perfil === 'PRESIDENTE' ? 'CONFIRMADO' : usuario?.perfil === 'FINANCEIRO' ? 'DEFERIDO' : usuario?.perfil === 'ADMINISTRADOR' ? 'SOLICITADO' : ''
  const [status, setStatus] = useState<StatusDiaria | ''>(padrao)
  const [f, setF] = useState({ de: '', ate: '', texto: '' })
  const [aplicado, setAplicado] = useState(f)
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['diarias', status, aplicado, page], queryFn: () => api.diarias.listar({ status, ...aplicado }, page) })
  const cont = useQuery({ queryKey: ['diarias-contagens'], queryFn: api.diarias.contagens })
  return (
    <>
      <PageHeader titulo="Diárias" subtitulo="Solicitações de diárias de viagem e seu fluxo de aprovação." acoes={temPerfil('ADMINISTRADOR', 'FISCAL') && <Link className="btn btn-primary" to="/diarias/nova"><i className="bi bi-plus-lg"></i> Nova solicitação</Link>} />
      <div className="d-flex gap-2 mb-3 flex-wrap">
        <button className={`btn btn-sm ${status === '' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => { setStatus(''); setPage(0) }}>Todas</button>
        {STATUS.map(s => <button key={s} className={`btn btn-sm ${status === s ? 'btn-primary' : 'btn-secondary'}`} onClick={() => { setStatus(s); setPage(0) }}>{rotulo(s)}{cont.data ? ` (${cont.data[s] ?? 0})` : ''}</button>)}
      </div>
      <Filtro onSubmit={() => { setAplicado(f); setPage(0) }} onLimpar={() => { const v = { de: '', ate: '', texto: '' }; setF(v); setAplicado(v) }}>
        <div className="col-md-2"><label className="form-label small">Partida de</label><input type="date" className="form-control form-control-sm" value={f.de} onChange={e => setF({ ...f, de: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Até</label><input type="date" className="form-control form-control-sm" value={f.ate} onChange={e => setF({ ...f, ate: e.target.value })} /></div>
        <div className="col-md-7"><label className="form-label small">Profissional / itinerário / solicitante</label><input className="form-control form-control-sm" value={f.texto} onChange={e => setF({ ...f, texto: e.target.value })} /></div>
      </Filtro>
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-cash-coin" titulo="Nenhuma diária" desc="Nenhuma solicitação neste filtro." /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>#</th><th>Solicitada em</th><th>Profissional</th><th>Itinerário</th><th>Período</th><th className="text-end">Diárias</th><th className="text-end">Total</th><th>Solicitante</th><th>Status</th><th></th></tr></thead>
              <tbody>{q.data!.content.map(d => <tr key={d.id}>
                <td>{d.id}</td><td className="text-nowrap">{data(d.dataSolicitacao)}</td>
                <td><div className="cell-stack"><span className="cell-primary">{d.profissionalNome}</span><span className="cell-secondary">{d.profissionalNivel}{d.profissionalLotacao ? ` · ${d.profissionalLotacao}` : ''}</span></div></td>
                <td className="texto-limitado">{d.itinerario}</td>
                <td className="text-nowrap">{data(d.partida)} → {data(d.retorno)}</td>
                <td className="text-end">{numero(d.numeroDiarias, 1)} × {moeda(d.valorUnitario)}</td><td className="text-end fw-semibold">{moeda(d.valorTotal)}</td>
                <td>{d.solicitante}</td><td><Badge cor={badgeStatusDiaria[d.status]}>{rotulo(d.status)}</Badge></td>
                <td className="text-end"><Link className="btn btn-sm btn-secondary" to={`/diarias/${d.id}`}><i className="bi bi-eye"></i> Abrir</Link></td>
              </tr>)}</tbody>
            </table></div>
            <div className="px-3 pb-3"><div className="small text-muted mt-2">{q.data!.totalElements} registro(s)</div><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

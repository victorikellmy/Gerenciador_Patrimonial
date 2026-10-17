import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { Card, Carregando, EmptyState, Erro, Filtro, Kpi, PageHeader } from '../../components/ui'
import { data, moeda, numero } from '../../utils/format'

export default function DiariasPagas() {
  const [f, setF] = useState({ de: '', ate: '' })
  const [aplicado, setAplicado] = useState(f)
  const q = useQuery({ queryKey: ['diarias-pagas', aplicado], queryFn: () => api.diarias.pagas(aplicado.de || undefined, aplicado.ate || undefined) })
  const de = q.data?.de ?? aplicado.de, ate = q.data?.ate ?? aplicado.ate
  return (
    <>
      <PageHeader titulo="Diárias pagas" subtitulo="Relação de diárias pagas no período (padrão: mês atual)." acoes={<>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.diarias.pagasUrl(de, ate, 'xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.diarias.pagasUrl(de, ate, 'pdf'))}><i className="bi bi-file-earmark-pdf"></i> PDF</button>
      </>} />
      <Filtro onSubmit={() => setAplicado(f)} onLimpar={() => { setF({ de: '', ate: '' }); setAplicado({ de: '', ate: '' }) }}>
        <div className="col-md-3"><label className="form-label small">Pagas de</label><input type="date" className="form-control form-control-sm" value={f.de} onChange={e => setF({ ...f, de: e.target.value })} /></div>
        <div className="col-md-3"><label className="form-label small">Até</label><input type="date" className="form-control form-control-sm" value={f.ate} onChange={e => setF({ ...f, ate: e.target.value })} /></div>
      </Filtro>
      {q.data && <div className="row g-3 mb-4"><div className="col-md-4"><Kpi icon="bi-cash-stack" cor="success" label={`Total pago · ${data(q.data.de)} a ${data(q.data.ate)}`} valor={moeda(q.data.total)} extra={`${q.data.lista.length} diária(s)`} /></div></div>}
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : !q.data?.lista.length ? <EmptyState icon="bi-receipt-cutoff" titulo="Nenhuma diária paga no período" /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>#</th><th>Pago em</th><th>Profissional</th><th>Itinerário</th><th>Período</th><th className="text-end">Diárias</th><th className="text-end">Unitário</th><th className="text-end">Total</th></tr></thead>
            <tbody>{q.data.lista.map(d => <tr key={d.id}><td><Link to={`/diarias/${d.id}`}>{d.id}</Link></td><td className="text-nowrap">{data(d.dataPagamento)}</td><td><div className="cell-stack"><span className="cell-primary">{d.profissionalNome}</span><span className="cell-secondary">{d.profissionalNivel}</span></div></td><td className="texto-limitado">{d.itinerario}</td><td className="text-nowrap">{data(d.partida)} → {data(d.retorno)}</td><td className="text-end">{numero(d.numeroDiarias, 1)}</td><td className="text-end">{moeda(d.valorUnitario)}</td><td className="text-end fw-semibold">{moeda(d.valorTotal)}</td></tr>)}</tbody>
          </table></div>)}
      </Card>
    </>
  )
}

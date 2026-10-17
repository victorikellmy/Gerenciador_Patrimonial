import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bar } from 'react-chartjs-2'
import { Chart as ChartJS, BarElement, CategoryScale, LinearScale, Tooltip, Legend } from 'chart.js'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { Card, Carregando, Erro, Kpi, PageHeader, Section } from '../../components/ui'
import { data, hora, moeda, numero } from '../../utils/format'

ChartJS.register(BarElement, CategoryScale, LinearScale, Tooltip, Legend)
const estadoBadge: Record<string, string> = { NA_RUA: 'text-bg-info', MANUTENCAO: 'text-bg-warning', DISPONIVEL: 'text-bg-success', INATIVO: 'text-bg-secondary' }
const estadoRotulo: Record<string, string> = { NA_RUA: 'Na rua', MANUTENCAO: 'Em manutenção', DISPONIVEL: 'Disponível', INATIVO: 'Inativo' }

export default function FrotaGestao() {
  const q = useQuery({ queryKey: ['frota-gestao'], queryFn: api.frota.gestao, refetchInterval: 120000 })
  if (q.isLoading) return <Carregando />
  if (q.error || !q.data) return <Erro erro={q.error} />
  const g = q.data
  return (
    <>
      <PageHeader titulo="Gestão da frota" subtitulo={`Situação em ${data(g.hoje)} · gastos do mês e do ano.`} acoes={<>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.frota.exportUrl('gestao', {}, 'xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.frota.exportUrl('gestao', {}, 'pdf'))}><i className="bi bi-file-earmark-pdf"></i> PDF</button>
      </>} />
      <div className="row g-3 mb-4">
        <div className="col-6 col-xl-2"><Kpi icon="bi-truck-front" label="Veículos ativos" valor={g.veiculosAtivos} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-signpost-2" cor="info" label="Na rua agora" valor={g.naRua} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-wrench-adjustable" cor="warning" label="Em manutenção" valor={g.emManutencao} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-check-circle" cor="success" label="Disponíveis" valor={g.disponiveis} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-exclamation-triangle" cor="danger" label="Revisões vencidas" valor={g.revisoesVencidas} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-speedometer" label="Km no mês / ano" valor={numero(g.kmMes)} extra={`${numero(g.kmAno)} km no ano`} /></div>
      </div>
      <div className="row g-3 mb-4">
        <div className="col-md-6 col-xl-3"><Kpi icon="bi-fuel-pump" cor="primary" label="Combustível no mês" valor={moeda(g.combustivelMes)} extra={`${moeda(g.combustivelAno)} no ano`} /></div>
        <div className="col-md-6 col-xl-3"><Kpi icon="bi-tools" cor="warning" label="Manutenção no mês" valor={moeda(g.manutencaoMes)} extra={`${moeda(g.manutencaoAno)} no ano`} /></div>
        <div className="col-md-6 col-xl-3"><Kpi icon="bi-cash-stack" cor="success" label="Total no ano" valor={moeda(Number(g.combustivelAno) + Number(g.manutencaoAno))} /></div>
        <div className="col-md-6 col-xl-3"><Kpi icon="bi-calculator" cor="info" label="Custo médio por km (ano)" valor={g.custoMedioPorKmAno ? moeda(g.custoMedioPorKmAno) : '—'} /></div>
      </div>
      {g.alertasRevisao.length > 0 && <div className="alert alert-danger"><i className="bi bi-exclamation-triangle-fill"></i> <strong>Revisão vencida:</strong> {g.alertasRevisao.map(a => <Link key={a.veiculoId} className="alert-link me-2" to={`/frota/${a.veiculoId}`}>{a.placa} ({a.modelo})</Link>)}</div>}
      <div className="row g-3 mb-4">
        <div className="col-12">
          <Card><Section icon="bi-truck-front" titulo="Situação dos veículos" desc="Quem está com qual viatura, onde foi e desde quando." semBorda>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Veículo</th><th>Cidade</th><th>Estado</th><th>Motorista</th><th>Destino</th><th>Saída</th><th className="text-end">Km atual</th><th>Última saída</th><th>Última manutenção</th><th>Próx. revisão</th></tr></thead>
              <tbody>{g.situacoes.map(s => (
                <tr key={s.veiculoId} className={s.estado === 'NA_RUA' ? 'row-na-rua' : s.estado === 'MANUTENCAO' ? 'row-manutencao' : ''}>
                  <td><Link to={`/frota/${s.veiculoId}`} className="text-decoration-none"><div className="cell-stack"><span className="cell-primary">{s.placa}</span><span className="cell-secondary">{s.modelo}</span></div></Link></td>
                  <td>{s.cidade || '—'}</td>
                  <td><span className={`badge badge-plain ${estadoBadge[s.estado]}`}>{estadoRotulo[s.estado]}</span></td>
                  <td>{s.motorista || '—'}</td><td className="texto-limitado">{s.destino || '—'}</td>
                  <td className="text-nowrap">{s.desde ? `${data(s.desde)} ${hora(s.horaSaida)}` : '—'}</td>
                  <td className="text-end">{numero(s.kmAtual)}</td><td>{data(s.ultimaSaida)}</td>
                  <td><div className="cell-stack"><span className="cell-primary">{s.ultimaManutencao || '—'}</span><span className="cell-secondary">{data(s.dataUltimaManutencao)}</span></div></td>
                  <td className={s.revisaoVencida ? 'text-danger fw-semibold' : ''}>{s.proximaRevisaoKm ? `${numero(s.proximaRevisaoKm)} km` : ''}{s.proximaRevisaoKm && s.proximaRevisaoData ? ' / ' : ''}{s.proximaRevisaoData ? data(s.proximaRevisaoData) : ''}{!s.proximaRevisaoKm && !s.proximaRevisaoData && '—'}</td>
                </tr>))}</tbody>
            </table></div>
          </Section></Card>
        </div>
      </div>
      <div className="row g-3 align-items-start">
        <div className="col-xl-6">
          <Card><Section icon="bi-bar-chart" titulo="Gastos por mês" desc="Últimos 12 meses." semBorda>
            <div style={{ height: 320 }}>
              <Bar data={{ labels: g.gastosPorMes.map(m => m.rotulo), datasets: [{ label: 'Combustível', data: g.gastosPorMes.map(m => Number(m.combustivel)), backgroundColor: '#1f6f5f' }, { label: 'Manutenção', data: g.gastosPorMes.map(m => Number(m.manutencao)), backgroundColor: '#f4b740' }] }} options={{ responsive: true, maintainAspectRatio: false, scales: { x: { stacked: true }, y: { stacked: true } } }} />
            </div>
          </Section></Card>
        </div>
        <div className="col-xl-6">
          <Card><Section icon="bi-cash-coin" titulo="Gasto por veículo no ano" semBorda>
            <div className="table-responsive table-livre"><table className="table table-compacta mb-0">
              <thead><tr><th>Veículo</th><th className="text-end">Combustível</th><th className="text-end">Litros</th><th className="text-end">Manutenção</th><th className="text-end">Total</th><th className="text-end">Km</th><th className="text-end">R$/km</th></tr></thead>
              <tbody>{g.gastosPorVeiculo.map(v => <tr key={v.veiculoId}><td><Link to={`/frota/${v.veiculoId}`}>{v.placa}</Link> <span className="text-muted small">{v.modelo}</span></td><td className="text-end">{moeda(v.combustivel)}</td><td className="text-end">{numero(v.litros, 1)}</td><td className="text-end">{moeda(v.manutencao)} <span className="text-muted small">({v.manutencoes})</span></td><td className="text-end fw-semibold">{moeda(v.total)}</td><td className="text-end">{numero(v.kmRodados)}</td><td className="text-end">{v.custoPorKm ? moeda(v.custoPorKm) : '—'}</td></tr>)}</tbody>
            </table></div>
          </Section></Card>
        </div>
      </div>
    </>
  )
}

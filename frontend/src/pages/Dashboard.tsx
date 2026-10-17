import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Doughnut, Bar } from 'react-chartjs-2'
import { Chart as ChartJS, ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend } from 'chart.js'
import { api } from '../api'
import { useAuth } from '../auth/AuthContext'
import { Card, Carregando, Erro, Kpi, PageHeader } from '../components/ui'
import { data, moeda, numero, rotulo } from '../utils/format'

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend)
const CORES = ['#1f6f5f', '#2e8b74', '#4ea58b', '#7cc1a8', '#f4b740', '#e07a3f', '#c94c4c', '#6c757d', '#3b82f6', '#8b5cf6']

export default function Dashboard() {
  const { temPerfil } = useAuth()
  const q = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard })
  const me = useQuery({ queryKey: ['me-resumo'], queryFn: api.auth.resumo })
  if (q.isLoading) return <Carregando />
  if (q.error) return <Erro erro={q.error} />
  const d = q.data!
  const lev = me.data?.levantamentoAberto
  return (
    <>
      <PageHeader titulo="Dashboard" subtitulo="Visão geral do patrimônio da Fundação." />
      {temPerfil('ADMINISTRADOR', 'FISCAL') && d.pendenciasPatrimoniamento > 0 && (
        <div className="alert alert-warning d-flex align-items-center gap-2"><i className="bi bi-inbox-fill"></i><div><strong>{d.pendenciasPatrimoniamento}</strong> bem(ns) recebido(s) do Almoxarifado aguardando patrimoniamento. <Link to="/pendencias" className="alert-link">Ver pendências</Link></div></div>
      )}
      {lev && temPerfil('ADMINISTRADOR', 'FISCAL') && (
        <div className="alert alert-info d-flex align-items-center gap-2"><i className="bi bi-clipboard2-check-fill"></i><div>Levantamento patrimonial <strong>{lev.ano}</strong> em andamento: {lev.total - lev.pendentes} de {lev.total} bens conferidos. <Link to={`/levantamentos/${lev.id}`} className="alert-link">Continuar</Link></div></div>
      )}
      <div className="row g-3 mb-4">
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-box-seam" label="Bens cadastrados" valor={numero(d.totalPatrimonios)} extra={`${numero(d.contagemPorSituacao?.ATIVO ?? 0)} ativos · ${numero(d.contagemPorSituacao?.BAIXADO ?? 0)} baixados`} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-cash-stack" cor="success" label="Valor de aquisição (ativos)" valor={moeda(d.valorTotalAtivos)} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-graph-down-arrow" cor="warning" label="Depreciação acumulada" valor={moeda(d.depreciacaoAcumuladaTotal)} extra={`Valor contábil líquido ${moeda(d.valorContabilLiquidoTotal)}`} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-building" cor="info" label="Lotações / responsáveis" valor={`${numero(d.totalLotacoes)} / ${numero(d.totalResponsaveis)}`} /></div>
      </div>
      <div className="row g-3 mb-4">
        <div className="col-lg-4">
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-3">Por situação</h6>
            <Doughnut data={{ labels: Object.keys(d.contagemPorSituacao).map(rotulo), datasets: [{ data: Object.values(d.contagemPorSituacao), backgroundColor: CORES }] }} options={{ plugins: { legend: { position: 'bottom' } } }} />
          </div></Card>
        </div>
        <div className="col-lg-4">
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-3">Por estado de conservação</h6>
            <Doughnut data={{ labels: d.porConservacao.map(a => rotulo(a.chave)), datasets: [{ data: d.porConservacao.map(a => a.quantidade), backgroundColor: CORES }] }} options={{ plugins: { legend: { position: 'bottom' } } }} />
          </div></Card>
        </div>
        <div className="col-lg-4">
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-3">Top UPMs</h6>
            <Bar data={{ labels: d.topUpms.map(a => a.chave), datasets: [{ label: 'Bens', data: d.topUpms.map(a => a.quantidade), backgroundColor: '#1f6f5f' }] }} options={{ indexAxis: 'y', plugins: { legend: { display: false } } }} />
          </div></Card>
        </div>
      </div>
      <div className="row g-3">
        <div className="col-lg-5">
          <Card><div className="card-body"><h6 className="section-title mb-3">Por categoria</h6>
            <div className="table-responsive table-livre"><table className="table table-sm table-compacta mb-0"><thead><tr><th>Categoria</th><th className="text-end">Qtd.</th><th className="text-end">Valor</th></tr></thead>
              <tbody>{d.porCategoria.map(c => <tr key={c.chave}><td>{c.chave}</td><td className="text-end">{numero(c.quantidade)}</td><td className="text-end">{moeda(c.valorTotal)}</td></tr>)}</tbody></table></div>
          </div></Card>
        </div>
        <div className="col-lg-7">
          <Card><div className="card-body"><h6 className="section-title mb-3">Últimas movimentações</h6>
            {d.ultimasMovimentacoes.length === 0 ? <div className="text-muted small">Nenhuma movimentação registrada.</div> :
              <div className="table-responsive table-livre"><table className="table table-sm table-compacta mb-0"><thead><tr><th>Data</th><th>Bem</th><th>De</th><th>Para</th></tr></thead>
                <tbody>{d.ultimasMovimentacoes.map(m => <tr key={m.id}><td className="text-nowrap">{data(m.dataMovimentacao)}</td><td><span className="chip-tombo me-1">{m.patrimonioTombo}</span>{m.patrimonioDescricao}</td><td>{m.deLotacao}</td><td>{m.paraLotacao}</td></tr>)}</tbody></table></div>}
          </div></Card>
        </div>
      </div>
    </>
  )
}

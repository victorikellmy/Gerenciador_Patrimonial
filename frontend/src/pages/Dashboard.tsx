import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Bar } from 'react-chartjs-2'
import { Chart as ChartJS, BarElement, CategoryScale, LinearScale, Tooltip } from 'chart.js'
import { api } from '../api'
import { useAuth } from '../auth/AuthContext'
import { Card, Carregando, Erro, Kpi, PageHeader } from '../components/ui'
import Rosca, { type FatiaRosca } from '../components/Rosca'
import { data, moeda, numero, rotulo, CONSERVACOES, SITUACOES } from '../utils/format'

ChartJS.register(BarElement, CategoryScale, LinearScale, Tooltip)

// Cores com significado: verde = em uso/bom, âmbar = atenção, vermelho = problema, cinza = fora de uso / sem dado.
const COR_SITUACAO: Record<string, string> = { ATIVO: '#2e8b74', MANUTENCAO: '#f4b740', CAUTELADO: '#3b82f6', EM_APURACAO: '#c94c4c', BAIXADO: '#6c757d' }
const COR_CONSERVACAO: Record<string, string> = { NOVO: '#1f6f5f', OTIMO: '#2e8b74', BOM: '#4ea58b', BOM_REGULAR: '#8fcf4a', REGULAR: '#f4b740', REGULAR_RUIM: '#f08c2e', RUIM: '#e0533f', INSERVIVEL: '#a12c2c' }
const COR_SEM_INFO = '#8a94a6'
const corTexto = (token: string) => getComputedStyle(document.documentElement).getPropertyValue(token).trim() || '#888'

function fatiasSituacao(cont: Record<string, number>): FatiaRosca[] {
  const ordem = [...SITUACOES, ...Object.keys(cont).filter(k => !(SITUACOES as readonly string[]).includes(k))]
  return ordem.map(k => ({ chave: k, rotulo: rotulo(k), quantidade: cont[k] ?? 0, cor: COR_SITUACAO[k] ?? COR_SEM_INFO }))
}
function fatiasConservacao(lista: { chave: string; quantidade: number }[]): FatiaRosca[] {
  const porChave = Object.fromEntries(lista.map(a => [a.chave, a.quantidade]))
  const conhecidas = CONSERVACOES.map(k => ({ chave: k, rotulo: rotulo(k), quantidade: porChave[k] ?? 0, cor: COR_CONSERVACAO[k] }))
  const outras = lista.filter(a => !(CONSERVACOES as readonly string[]).includes(a.chave)).map(a => ({ chave: a.chave || 'SEM', rotulo: a.chave && a.chave !== '(não informado)' ? rotulo(a.chave) : 'Não informado', quantidade: a.quantidade, cor: COR_SEM_INFO }))
  return [...conhecidas, ...outras]
}

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
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-1">Por situação</h6><div className="small text-muted mb-3">Em que estado operacional os bens estão.</div>
            <Rosca fatias={fatiasSituacao(d.contagemPorSituacao)} />
          </div></Card>
        </div>
        <div className="col-lg-4">
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-1">Por estado de conservação</h6><div className="small text-muted mb-3">Do melhor (verde) ao pior (vermelho).</div>
            <Rosca fatias={fatiasConservacao(d.porConservacao)} />
          </div></Card>
        </div>
        <div className="col-lg-4">
          <Card className="h-100"><div className="card-body"><h6 className="section-title mb-1">Unidades com mais bens</h6><div className="small text-muted mb-3">Quantidade de bens por UPM.</div>
            <div className="grafico-barras">
              <Bar data={{ labels: d.topUpms.map(a => a.chave), datasets: [{ label: 'Bens', data: d.topUpms.map(a => a.quantidade), backgroundColor: '#2e8b74', borderRadius: 4 }] }}
                   options={{ indexAxis: 'y', responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: ctx => ` ${numero(ctx.parsed.x)} bens` } } },
                     scales: { x: { ticks: { color: corTexto('--text-muted') }, grid: { color: corTexto('--border') } }, y: { ticks: { color: corTexto('--text-primary'), autoSkip: false, callback: (_v, i) => { const l = d.topUpms[i]?.chave ?? ''; return l.length > 14 ? l.slice(0, 13) + '…' : l } }, grid: { display: false } } } }} />
            </div>
            <ul className="list-unstyled small mb-0 mt-3">{d.topUpms.map(a => <li key={a.chave} className="d-flex justify-content-between border-top py-1"><span>{a.chave}</span><strong>{numero(a.quantidade)}</strong></li>)}</ul>
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

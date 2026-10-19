import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, Kpi, PageHeader, useToast } from '../../components/ui'
import { data, hora, moeda, numero, rotulo } from '../../utils/format'
import { BotaoBaixarApp } from '../../components/BaixarApp'

export default function FrotaVeiculos() {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [todos, setTodos] = useState(false)
  const [busca, setBusca] = useState('')
  const veiculos = useQuery({ queryKey: ['veiculos', todos], queryFn: () => api.frota.veiculos(todos) })
  const resumo = useQuery({ queryKey: ['frota-resumo'], queryFn: api.frota.resumo })
  const importacao = useQuery({ queryKey: ['frota-importacao'], queryFn: api.frota.importacaoConfigurada, enabled: temPerfil('ADMINISTRADOR') })
  const [importando, setImportando] = useState(false)
  const importar = async () => {
    if (!confirm('Importar dados do sistema Transportes? Registros já importados são ignorados.')) return
    setImportando(true)
    try { const r = await api.frota.importarTransportes(); toast.sucesso(`Importação concluída: ${r.veiculosNovos} veículo(s) novo(s), ${r.abastecimentos} abastecimento(s), ${r.manutencoes} manutenção(ões), ${r.deslocamentos} deslocamento(s), ${r.diarias ?? 0} diária(s), ${r.cautelas ?? 0} cautela(s).`); qc.invalidateQueries() }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha na importação.') } finally { setImportando(false) }
  }
  const lista = (veiculos.data ?? []).filter(v => !busca || `${v.placa} ${v.modelo} ${v.marca ?? ''} ${v.cidade ?? ''}`.toLowerCase().includes(busca.toLowerCase()))
  const r = resumo.data
  return (
    <>
      <PageHeader titulo="Frota" subtitulo="Veículos da Fundação com histórico de deslocamentos, abastecimentos e manutenções." acoes={<>
        <BotaoBaixarApp />
        {temPerfil('ADMINISTRADOR') && importacao.data?.configurada && <button className="btn btn-secondary" disabled={importando} onClick={importar}><i className="bi bi-cloud-download"></i> Importar do Transportes</button>}
        {temPerfil('ADMINISTRADOR', 'FISCAL') && <Link className="btn btn-primary" to="/frota/veiculos/novo"><i className="bi bi-plus-lg"></i> Novo veículo</Link>}
      </>} />
      {r && <div className="row g-3 mb-4">
        <div className="col-6 col-xl-2"><Kpi icon="bi-truck-front" label="Veículos ativos" valor={r.veiculosAtivos} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-signpost-2" cor="info" label="Na rua agora" valor={r.naRua} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-geo" cor="success" label="Saídas no mês" valor={r.deslocamentosMes} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-fuel-pump" label="Combustível no mês" valor={moeda(r.combustivelMes)} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-wrench-adjustable" cor="warning" label="Manutenção no mês" valor={moeda(r.manutencaoMes)} extra={`${r.manutencoesMes} serviço(s)`} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-clock-history" cor="danger" label="Em andamento" valor={r.emAndamento.length} /></div>
      </div>}
      {r && r.emAndamento.length > 0 && <Card className="mb-4"><div className="p-3">
        <div className="form-section-title mb-2"><i className="bi bi-signpost-2 me-1"></i> Viaturas na rua</div>
        <div className="d-flex flex-wrap gap-2">{r.emAndamento.map(d => <Link key={d.id} to={`/frota/${d.veiculoId}`} className="badge badge-plain text-bg-info text-decoration-none p-2">{d.placa} · {d.motorista} → {d.destino} ({data(d.dataViagem)} {hora(d.horaSaida)})</Link>)}</div>
      </div></Card>}
      <Card>
        <div className="p-3 border-bottom d-flex flex-wrap gap-2 align-items-center">
          <input className="form-control form-control-sm" style={{ maxWidth: 280 }} placeholder="Buscar por placa, modelo ou cidade" value={busca} onChange={e => setBusca(e.target.value)} />
          <div className="form-check form-switch ms-auto mb-0"><input className="form-check-input" type="checkbox" id="todos" checked={todos} onChange={e => setTodos(e.target.checked)} /><label className="form-check-label small" htmlFor="todos">Incluir inativos</label></div>
        </div>
        {veiculos.isLoading ? <Carregando /> : veiculos.error ? <Erro erro={veiculos.error} /> : !lista.length ? <EmptyState icon="bi-truck-front" titulo="Nenhum veículo" ctaTo={temPerfil('ADMINISTRADOR', 'FISCAL') ? '/frota/veiculos/novo' : undefined} ctaLabel="Novo veículo" /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Placa</th><th>Modelo</th><th>Cidade</th><th>Tombo</th><th>Situação</th><th className="text-end">Km atual</th><th>Última saída</th><th></th></tr></thead>
            <tbody>{lista.map(v => (
              <tr key={v.id} className={v.naRua ? 'row-na-rua' : v.situacao === 'MANUTENCAO' ? 'row-manutencao' : ''}>
                <td><Link to={`/frota/${v.id}`} className="chip-tombo text-decoration-none">{v.placa}</Link></td>
                <td><div className="cell-stack"><span className="cell-primary">{v.modelo}</span><span className="cell-secondary">{v.marca}{v.ano ? ` · ${v.ano}` : ''}{v.combustivelPadrao ? ` · ${rotulo(v.combustivelPadrao)}` : ''}</span></div></td>
                <td>{v.cidade || '—'}</td><td>{v.numeroTombo || '—'}</td>
                <td>{!v.ativo ? <span className="badge badge-plain text-bg-secondary">Inativo</span> : v.naRua ? <span className="badge badge-plain text-bg-info">Na rua</span> : v.situacao === 'MANUTENCAO' ? <span className="badge badge-plain text-bg-warning">Em manutenção</span> : <span className="badge badge-plain text-bg-success">Disponível</span>}</td>
                <td className="text-end">{numero(v.kmAtual)}</td><td>{data(v.ultimaSaida)}</td>
                <td className="text-end text-nowrap">
                  <Link className="btn btn-sm btn-secondary btn-icon" title="Ficha" to={`/frota/${v.id}`}><i className="bi bi-eye"></i></Link>{' '}
                  {v.ativo && !v.naRua && <Link className="btn btn-sm btn-primary btn-icon" title="Registrar saída" to={`/frota/${v.id}/deslocamentos/novo`}><i className="bi bi-signpost-2"></i></Link>}
                </td>
              </tr>))}</tbody>
          </table></div>)}
      </Card>
    </>
  )
}

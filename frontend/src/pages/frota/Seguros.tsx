import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, Kpi, PageHeader } from '../../components/ui'
import { badgeSituacaoSeguro, badgeSituacaoSinistro, data, moeda, prazoSeguro, rotulo } from '../../utils/format'

/** Seguro da frota: alertas de vencimento, todas as apólices e todos os sinistros. */
export default function Seguros() {
  const { temPerfil } = useAuth()
  const gestor = temPerfil('ADMINISTRADOR', 'FISCAL', 'PRESIDENTE')
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba') === 'sinistros' ? 'sinistros' : 'apolices'
  const [situacao, setSituacao] = useState('')
  const [texto, setTexto] = useState('')
  const alertas = useQuery({ queryKey: ['seguros-alertas'], queryFn: api.frota.alertasSeguro })
  const seguros = useQuery({ queryKey: ['seguros', { situacao, texto }], queryFn: () => api.frota.seguros({ situacao, texto }) })
  const sinistros = useQuery({ queryKey: ['sinistros', 'todos'], queryFn: () => api.frota.sinistros(), enabled: aba === 'sinistros' })
  const a = alertas.data
  return (
    <>
      <PageHeader titulo="Seguros da frota" subtitulo="Apólices dos veículos, vencimentos e sinistros."
        acoes={gestor && <><Link className="btn btn-primary" to="/frota/seguros/novo"><i className="bi bi-plus-lg"></i> Novo seguro</Link><Link className="btn btn-secondary" to="/frota/sinistros/novo"><i className="bi bi-car-front"></i> Registrar sinistro</Link></>} />
      {a && <div className="row g-3 mb-3">
        <div className="col-6 col-xl-3"><Kpi icon="bi-shield-x" cor="danger" label="Seguro vencido" valor={a.vencidos.length} extra="sem renovação" /></div>
        <div className="col-6 col-xl-3"><Kpi icon="bi-hourglass-split" cor="warning" label={`Vence em até ${a.diasAviso} dias`} valor={a.vencendo.length} /></div>
        <div className="col-6 col-xl-3"><Kpi icon="bi-shield-exclamation" cor="danger" label="Veículos sem seguro" valor={a.semSeguro.length} /></div>
        <div className="col-6 col-xl-3"><Kpi icon="bi-car-front" cor="info" label="Sinistros em andamento" valor={a.sinistrosEmAndamento.length} /></div>
      </div>}
      {a && (a.vencidos.length > 0 || a.vencendo.length > 0 || a.semSeguro.length > 0) && (
        <div className="alert alert-warning d-block">
          {a.vencidos.length > 0 && <div><i className="bi bi-shield-x"></i> <strong>Vencido:</strong> {a.vencidos.map(s => <Link key={s.id} className="alert-link me-2" to={`/frota/${s.veiculoId}?aba=seguro`}>{s.placa} ({prazoSeguro(s)})</Link>)}</div>}
          {a.vencendo.length > 0 && <div><i className="bi bi-hourglass-split"></i> <strong>Vencendo:</strong> {a.vencendo.map(s => <Link key={s.id} className="alert-link me-2" to={`/frota/${s.veiculoId}?aba=seguro`}>{s.placa} ({prazoSeguro(s)})</Link>)}</div>}
          {a.semSeguro.length > 0 && <div><i className="bi bi-shield-exclamation"></i> <strong>Sem seguro:</strong> {a.semSeguro.map(v => <Link key={v.veiculoId} className="alert-link me-2" to={`/frota/${v.veiculoId}?aba=seguro`}>{v.placa} · {v.modelo}</Link>)}</div>}
        </div>)}
      <Card>
        <div className="px-3 pt-2 border-bottom d-flex flex-wrap gap-2 align-items-end">
          <ul className="nav nav-tabs border-0 me-auto">
            <li className="nav-item"><button className={`nav-link ${aba === 'apolices' ? 'active' : ''}`} onClick={() => setParams({}, { replace: true })}><i className="bi bi-files me-1"></i> Apólices</button></li>
            <li className="nav-item"><button className={`nav-link ${aba === 'sinistros' ? 'active' : ''}`} onClick={() => setParams({ aba: 'sinistros' }, { replace: true })}><i className="bi bi-car-front me-1"></i> Sinistros</button></li>
          </ul>
          {aba === 'apolices' && <div className="d-flex gap-2 mb-2">
            <select className="form-select form-select-sm" style={{ width: 160 }} value={situacao} onChange={e => setSituacao(e.target.value)}><option value="">Todas as situações</option>{['VIGENTE', 'VENCENDO', 'VENCIDO', 'FUTURO'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select>
            <input className="form-control form-control-sm" style={{ width: 220 }} placeholder="Placa, seguradora, apólice..." value={texto} onChange={e => setTexto(e.target.value)} />
          </div>}
        </div>
        {aba === 'apolices' ? (
          seguros.isLoading ? <Carregando /> : seguros.error ? <Erro erro={seguros.error} /> : !seguros.data?.length ? <EmptyState icon="bi-shield-check" titulo="Nenhuma apólice" ctaTo={gestor ? '/frota/seguros/novo' : undefined} ctaLabel="Novo seguro" /> : (
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Veículo</th><th>Seguradora</th><th>Apólice</th><th>Vigência</th><th>Situação</th><th>Assistência 24h</th><th className="text-end">Prêmio</th><th className="text-end">Franquia</th><th></th></tr></thead>
              <tbody>{seguros.data.map(s => <tr key={s.id}>
                <td><Link to={`/frota/${s.veiculoId}?aba=seguro`}>{s.placa}</Link> <span className="text-muted small">{s.modelo}</span></td>
                <td>{s.seguradora}</td><td>{s.numeroApolice}</td>
                <td className="text-nowrap"><div className="cell-stack"><span className="cell-primary">{data(s.inicioVigencia)} a {data(s.fimVigencia)}</span><span className="cell-secondary">{prazoSeguro(s)}</span></div></td>
                <td><span className={`badge badge-plain ${badgeSituacaoSeguro[s.situacao]}`}>{rotulo(s.situacao)}</span></td>
                <td className="text-nowrap">{s.telefoneAssistencia || '—'}</td>
                <td className="text-end text-nowrap">{s.valorPremio != null ? moeda(s.valorPremio) : '—'}</td><td className="text-end text-nowrap">{s.franquia != null ? moeda(s.franquia) : '—'}</td>
                <td className="text-end text-nowrap">
                  {s.possuiApolice && <button className="btn btn-sm btn-secondary btn-icon" title="Apólice" onClick={() => abrirArquivo(api.frota.apoliceUrl(s.id))}><i className="bi bi-file-earmark-pdf"></i></button>}{' '}
                  {gestor && <Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={`/frota/seguros/${s.id}/editar`}><i className="bi bi-pencil"></i></Link>}
                </td></tr>)}</tbody>
            </table></div>)
        ) : (
          sinistros.isLoading ? <Carregando /> : sinistros.error ? <Erro erro={sinistros.error} /> : !sinistros.data?.length ? <EmptyState icon="bi-car-front" titulo="Nenhum sinistro" /> : (
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Data</th><th>Veículo</th><th>Ocorrência</th><th>Apólice</th><th>Situação</th><th className="text-end">Prejuízo</th><th className="text-end">Indenizado</th><th></th></tr></thead>
              <tbody>{sinistros.data.map(s => <tr key={s.id}>
                <td className="text-nowrap">{data(s.dataOcorrencia)}</td>
                <td><Link to={`/frota/${s.veiculoId}?aba=seguro`}>{s.placa}</Link> <span className="text-muted small">{s.modelo}</span></td>
                <td><div className="cell-stack"><span className="cell-primary">{s.descricao}</span><span className="cell-secondary">{[s.numeroSinistro && `protocolo ${s.numeroSinistro}`, s.localOcorrencia, s.condutor].filter(Boolean).join(' · ')}</span></div></td>
                <td>{s.numeroApolice ? `${s.seguradora} · ${s.numeroApolice}` : <span className="text-muted">sem cobertura</span>}</td>
                <td><span className={`badge badge-plain ${badgeSituacaoSinistro[s.situacao]}`}>{rotulo(s.situacao)}</span></td>
                <td className="text-end text-nowrap">{s.valorPrejuizo != null ? moeda(s.valorPrejuizo) : '—'}</td><td className="text-end text-nowrap">{s.valorIndenizado != null ? moeda(s.valorIndenizado) : '—'}</td>
                <td className="text-end">{gestor && <Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={`/frota/sinistros/${s.id}/editar`}><i className="bi bi-pencil"></i></Link>}</td>
              </tr>)}</tbody>
            </table></div>)
        )}
      </Card>
    </>
  )
}

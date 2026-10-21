import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import type { Seguro } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Carregando, useToast } from '../../components/ui'
import { badgeSituacaoSeguro, badgeSituacaoSinistro, data, moeda, prazoSeguro, rotulo } from '../../utils/format'

function Dado({ l, v }: { l: string; v: React.ReactNode }) { return <div className="col-6 col-md-4 col-xl-3"><div className="kpi-label">{l}</div><div className="fw-medium">{v || '—'}</div></div> }

/** Aba "Seguro" da ficha do veículo: apólice em vigor, histórico de apólices e sinistros. */
export default function AbaSeguro({ veiculoId }: { veiculoId: number }) {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const gestor = temPerfil('ADMINISTRADOR', 'FISCAL', 'PRESIDENTE')
  const admin = temPerfil('ADMINISTRADOR')
  const seguros = useQuery({ queryKey: ['seguros', { veiculoId }], queryFn: () => api.frota.seguros({ veiculoId }) })
  const sinistros = useQuery({ queryKey: ['sinistros', veiculoId], queryFn: () => api.frota.sinistros(veiculoId) })
  if (seguros.isLoading || sinistros.isLoading) return <Carregando />
  const lista = seguros.data ?? []
  const atual = lista.find(s => s.situacao === 'VIGENTE' || s.situacao === 'VENCENDO')
  const renovacao = lista.find(s => s.situacao === 'FUTURO')
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['seguros'] }); qc.invalidateQueries({ queryKey: ['sinistros'] }); qc.invalidateQueries({ queryKey: ['seguros-alertas'] }) }
  const excluirSeguro = async (s: Seguro) => { if (!confirm(`Excluir a apólice ${s.numeroApolice}?`)) return; try { await api.frota.excluirSeguro(s.id); toast.sucesso('Apólice excluída.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  const excluirSinistro = async (id: number) => { if (!confirm('Excluir este sinistro?')) return; try { await api.frota.excluirSinistro(id); toast.sucesso('Sinistro excluído.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }

  return (
    <div className="p-3">
      {atual ? (
        <div className={`border rounded-3 p-3 mb-4 ${atual.situacao === 'VENCENDO' ? 'border-warning' : 'border-success'}`}>
          <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
            <i className="bi bi-shield-check fs-4 text-success"></i>
            <div className="me-auto"><div className="fw-semibold">{atual.seguradora} · apólice {atual.numeroApolice}</div><div className="small text-muted">Vigência {data(atual.inicioVigencia)} a {data(atual.fimVigencia)} · {prazoSeguro(atual)}</div></div>
            <span className={`badge badge-plain ${badgeSituacaoSeguro[atual.situacao]}`}>{rotulo(atual.situacao)}</span>
            {atual.possuiApolice && <button className="btn btn-sm btn-secondary" onClick={() => abrirArquivo(api.frota.apoliceUrl(atual.id))}><i className="bi bi-file-earmark-pdf"></i> Apólice</button>}
            {gestor && <Link className="btn btn-sm btn-secondary" to={`/frota/seguros/${atual.id}/editar`}><i className="bi bi-pencil"></i> Editar</Link>}
          </div>
          {atual.situacao === 'VENCENDO' && !renovacao && <div className="alert alert-warning py-2 small mb-3"><i className="bi bi-exclamation-triangle"></i> O seguro {prazoSeguro(atual)}. {gestor && <Link className="alert-link" to={`/frota/seguros/novo?veiculoId=${veiculoId}&renovar=${atual.id}`}>Cadastrar a renovação</Link>}</div>}
          {renovacao && <div className="alert alert-info py-2 small mb-3"><i className="bi bi-arrow-repeat"></i> Renovação cadastrada: {renovacao.seguradora} · apólice {renovacao.numeroApolice}, a partir de {data(renovacao.inicioVigencia)}.</div>}
          <div className="row g-3">
            <Dado l="Assistência 24h" v={atual.telefoneAssistencia ? <a href={`tel:${atual.telefoneAssistencia.replace(/[^\d+]/g, '')}`}><i className="bi bi-telephone"></i> {atual.telefoneAssistencia}</a> : ''} />
            <Dado l="Franquia" v={atual.franquia != null ? moeda(atual.franquia) : ''} />
            <Dado l="Prêmio (valor pago)" v={atual.valorPremio != null ? moeda(atual.valorPremio) : ''} />
            <Dado l="Corretor" v={atual.corretor} />
          </div>
          {atual.coberturas && <div className="mt-3"><div className="kpi-label">Coberturas</div><div className="obs-pre">{atual.coberturas}</div></div>}
          {atual.observacao && <div className="mt-3"><div className="kpi-label">Observação</div><div className="obs-pre">{atual.observacao}</div></div>}
        </div>
      ) : (
        <div className="alert alert-danger d-flex flex-wrap align-items-center gap-2">
          <i className="bi bi-shield-exclamation fs-5"></i>
          <span className="me-auto"><strong>Veículo sem seguro em vigor.</strong>{renovacao ? ` A próxima apólice começa em ${data(renovacao.inicioVigencia)}.` : ''}</span>
          {gestor && <Link className="btn btn-sm btn-danger" to={`/frota/seguros/novo?veiculoId=${veiculoId}`}><i className="bi bi-plus-lg"></i> Cadastrar seguro</Link>}
        </div>
      )}

      <div className="d-flex align-items-center mb-2">
        <div className="form-section-title me-auto"><i className="bi bi-files me-1"></i> Apólices</div>
        {gestor && atual && <Link className="btn btn-sm btn-secondary" to={`/frota/seguros/novo?veiculoId=${veiculoId}&renovar=${atual.id}`}><i className="bi bi-plus-lg"></i> Nova apólice</Link>}
      </div>
      {!lista.length ? <div className="text-muted small mb-4">Nenhuma apólice cadastrada.</div> : (
        <div className="table-responsive table-livre mb-4"><table className="table table-hover table-compacta mb-0">
          <thead><tr><th>Seguradora</th><th>Apólice</th><th>Vigência</th><th>Situação</th><th className="text-end">Prêmio</th><th className="text-end">Franquia</th><th></th></tr></thead>
          <tbody>{lista.map(s => <tr key={s.id}>
            <td>{s.seguradora}</td><td>{s.numeroApolice}</td><td className="text-nowrap">{data(s.inicioVigencia)} a {data(s.fimVigencia)}</td>
            <td><span className={`badge badge-plain ${badgeSituacaoSeguro[s.situacao]}`}>{rotulo(s.situacao)}</span></td>
            <td className="text-end text-nowrap">{s.valorPremio != null ? moeda(s.valorPremio) : '—'}</td><td className="text-end text-nowrap">{s.franquia != null ? moeda(s.franquia) : '—'}</td>
            <td className="text-end text-nowrap">
              {s.possuiApolice && <button className="btn btn-sm btn-secondary btn-icon" title="Apólice" onClick={() => abrirArquivo(api.frota.apoliceUrl(s.id))}><i className="bi bi-file-earmark-pdf"></i></button>}{' '}
              {gestor && <Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={`/frota/seguros/${s.id}/editar`}><i className="bi bi-pencil"></i></Link>}{' '}
              {admin && <button className="btn btn-sm btn-outline-danger btn-icon" title="Excluir" onClick={() => excluirSeguro(s)}><i className="bi bi-trash"></i></button>}
            </td></tr>)}</tbody>
        </table></div>)}

      <div className="d-flex align-items-center mb-2">
        <div className="form-section-title me-auto"><i className="bi bi-car-front me-1"></i> Sinistros</div>
        {gestor && <Link className="btn btn-sm btn-secondary" to={`/frota/sinistros/novo?veiculoId=${veiculoId}`}><i className="bi bi-plus-lg"></i> Registrar sinistro</Link>}
      </div>
      {!sinistros.data?.length ? <div className="text-muted small">Nenhum sinistro registrado.</div> : (
        <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
          <thead><tr><th>Data</th><th>Ocorrência</th><th>Apólice</th><th>Situação</th><th className="text-end">Prejuízo</th><th className="text-end">Indenizado</th><th></th></tr></thead>
          <tbody>{sinistros.data.map(s => <tr key={s.id}>
            <td className="text-nowrap">{data(s.dataOcorrencia)}</td>
            <td><div className="cell-stack"><span className="cell-primary">{s.descricao}</span><span className="cell-secondary">{[s.numeroSinistro && `protocolo ${s.numeroSinistro}`, s.localOcorrencia, s.condutor].filter(Boolean).join(' · ')}</span></div></td>
            <td>{s.numeroApolice ? `${s.seguradora} · ${s.numeroApolice}` : <span className="text-muted">sem cobertura</span>}</td>
            <td><span className={`badge badge-plain ${badgeSituacaoSinistro[s.situacao]}`}>{rotulo(s.situacao)}</span></td>
            <td className="text-end text-nowrap">{s.valorPrejuizo != null ? moeda(s.valorPrejuizo) : '—'}</td><td className="text-end text-nowrap">{s.valorIndenizado != null ? moeda(s.valorIndenizado) : '—'}</td>
            <td className="text-end text-nowrap">
              {gestor && <Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={`/frota/sinistros/${s.id}/editar`}><i className="bi bi-pencil"></i></Link>}{' '}
              {admin && <button className="btn btn-sm btn-outline-danger btn-icon" title="Excluir" onClick={() => excluirSinistro(s.id)}><i className="bi bi-trash"></i></button>}
            </td></tr>)}</tbody>
        </table></div>)}
    </div>
  )
}

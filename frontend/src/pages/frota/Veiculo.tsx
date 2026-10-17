import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, Erro, Kpi, PageHeader, Section, useToast } from '../../components/ui'
import { agoraHora, data, hora, moeda, numero, rotulo } from '../../utils/format'

function Dado({ l, v }: { l: string; v: React.ReactNode }) { return <div className="col-6 col-md-4 col-xl-3"><div className="kpi-label">{l}</div><div className="fw-medium">{v || '—'}</div></div> }
const tipoIcone: Record<string, string> = { ABASTECIMENTO: 'bi-fuel-pump', MANUTENCAO: 'bi-wrench-adjustable', DESLOCAMENTO: 'bi-signpost-2' }
const tipoCor: Record<string, string> = { ABASTECIMENTO: 'text-bg-light', MANUTENCAO: 'text-bg-warning', DESLOCAMENTO: 'text-bg-info' }

export default function FrotaVeiculo() {
  const { id } = useParams()
  const vid = Number(id)
  const { temPerfil } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const ficha = useQuery({ queryKey: ['veiculo', id], queryFn: () => api.frota.veiculo(vid) })
  const hist = useQuery({ queryKey: ['veiculo-historico', id], queryFn: () => api.frota.historico(vid) })
  const [chegada, setChegada] = useState({ kmFinal: '', horaChegada: agoraHora() })
  const [manut, setManut] = useState<{ aberto: boolean; motivo: string }>({ aberto: false, motivo: '' })
  const [ocupado, setOcupado] = useState(false)
  const [filtro, setFiltro] = useState<'' | 'ABASTECIMENTO' | 'MANUTENCAO' | 'DESLOCAMENTO'>('')
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['veiculo', id] }); qc.invalidateQueries({ queryKey: ['veiculo-historico', id] }); qc.invalidateQueries({ queryKey: ['veiculos'] }); qc.invalidateQueries({ queryKey: ['frota-resumo'] }); qc.invalidateQueries({ queryKey: ['frota-gestao'] }) }
  const acao = async (fn: () => Promise<unknown>, msg: string) => { setOcupado(true); try { await fn(); toast.sucesso(msg); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) } }
  if (ficha.isLoading) return <Carregando />
  if (ficha.error || !ficha.data) return <Erro erro={ficha.error ?? 'Veículo não encontrado'} />
  const { veiculo: v, resumo: r } = ficha.data
  const gestor = temPerfil('ADMINISTRADOR', 'FISCAL')
  const opera = temPerfil('ADMINISTRADOR', 'FISCAL', 'MOTORISTA')
  const aberto = r.deslocamentoAberto
  const registrarChegada = (e: FormEvent) => { e.preventDefault(); if (!aberto) return; acao(() => api.frota.finalizarDeslocamento(aberto.id, Number(chegada.kmFinal), chegada.horaChegada), 'Chegada registrada.') }
  const historico = (hist.data ?? []).filter(h => !filtro || h.tipo === filtro)
  const editarUrl = (h: { tipo: string; id: number }) => h.tipo === 'ABASTECIMENTO' ? `/frota/abastecimentos/${h.id}/editar` : h.tipo === 'MANUTENCAO' ? `/frota/manutencoes/${h.id}/editar` : `/frota/deslocamentos/${h.id}/editar`
  const excluir = async (h: { tipo: string; id: number }) => {
    if (!confirm('Excluir este lançamento?')) return
    const fn = h.tipo === 'ABASTECIMENTO' ? api.frota.excluirAbastecimento : h.tipo === 'MANUTENCAO' ? api.frota.excluirManutencao : api.frota.excluirDeslocamento
    acao(() => fn(h.id), 'Lançamento excluído.')
  }
  return (
    <>
      <PageHeader voltar="/frota" titulo={<><span className="chip-tombo me-2">{v.placa}</span>{v.modelo}</>} subtitulo={<>{v.marca}{v.ano ? ` · ${v.ano}` : ''}{v.cidade ? ` · ${v.cidade}` : ''} · {!v.ativo ? <span className="badge badge-plain text-bg-secondary">Inativo</span> : v.naRua ? <span className="badge badge-plain text-bg-info">Na rua</span> : v.situacao === 'MANUTENCAO' ? <span className="badge badge-plain text-bg-warning">Em manutenção</span> : <span className="badge badge-plain text-bg-success">Disponível</span>}</>}
        acoes={<>
          {opera && v.ativo && !v.naRua && <Link className="btn btn-primary" to={`/frota/${v.id}/deslocamentos/novo`}><i className="bi bi-signpost-2"></i> Saída</Link>}
          {opera && v.ativo && <Link className="btn btn-secondary" to={`/frota/${v.id}/abastecimentos/novo`}><i className="bi bi-fuel-pump"></i> Abastecimento</Link>}
          {opera && v.ativo && <Link className="btn btn-secondary" to={`/frota/${v.id}/manutencoes/novo`}><i className="bi bi-wrench-adjustable"></i> Manutenção</Link>}
          {gestor && <Link className="btn btn-secondary" to={`/frota/${v.id}/editar`}><i className="bi bi-pencil"></i> Editar</Link>}
          {gestor && v.ativo && (v.situacao === 'MANUTENCAO' ? <button className="btn btn-outline-success" disabled={ocupado} onClick={() => acao(() => api.frota.manutencaoStatus(v.id, false), 'Veículo liberado da manutenção.')}><i className="bi bi-check2"></i> Liberar</button> : <button className="btn btn-outline-warning" onClick={() => setManut({ aberto: !manut.aberto, motivo: '' })}><i className="bi bi-wrench"></i> Para manutenção</button>)}
          {temPerfil('ADMINISTRADOR') && <button className={`btn ${v.ativo ? 'btn-outline-danger' : 'btn-outline-success'}`} disabled={ocupado} onClick={() => { if (confirm(v.ativo ? 'Desativar o veículo?' : 'Reativar o veículo?')) acao(() => api.frota.ativo(v.id, !v.ativo), v.ativo ? 'Veículo desativado.' : 'Veículo reativado.') }}>{v.ativo ? 'Desativar' : 'Reativar'}</button>}
          <button className="btn btn-secondary btn-icon" title="Histórico em PDF" onClick={() => abrirArquivo(api.frota.exportUrl('historico', { veiculoId: v.id }, 'pdf'))}><i className="bi bi-file-earmark-pdf"></i></button>
        </>} />
      {manut.aberto && <Card className="mb-3"><Section icon="bi-wrench" titulo="Colocar em manutenção" desc="O veículo fica indisponível para saídas até ser liberado." semBorda>
        <div className="row g-2"><div className="col-md-9"><input className="form-control" placeholder="Motivo (opcional)" value={manut.motivo} onChange={e => setManut({ ...manut, motivo: e.target.value })} /></div><div className="col-md-3 d-flex gap-2 justify-content-end"><button className="btn btn-secondary" onClick={() => setManut({ aberto: false, motivo: '' })}>Cancelar</button><button className="btn btn-warning" disabled={ocupado} onClick={() => acao(() => api.frota.manutencaoStatus(v.id, true, manut.motivo), 'Veículo em manutenção.').then(() => setManut({ aberto: false, motivo: '' }))}>Confirmar</button></div></div></Section></Card>}
      {r.revisaoVencida && <div className="alert alert-danger"><i className="bi bi-exclamation-triangle-fill"></i> <strong>Revisão vencida.</strong> Próxima revisão prevista {r.proximaRevisaoKm ? `aos ${numero(r.proximaRevisaoKm)} km` : ''}{r.proximaRevisaoKm && r.proximaRevisaoData ? ' ou ' : ''}{r.proximaRevisaoData ? `em ${data(r.proximaRevisaoData)}` : ''}.</div>}
      {aberto && <Card className="mb-3 border-info"><form onSubmit={registrarChegada}><Section icon="bi-signpost-2" titulo="Viatura na rua" desc={`${aberto.motorista} → ${aberto.destino} · saiu ${data(aberto.dataViagem)} às ${hora(aberto.horaSaida)} com ${numero(aberto.kmInicial)} km · ${aberto.motivo}`} semBorda>
        {opera && <div className="row g-2 align-items-end"><div className="col-md-3"><label className="form-label">Km de chegada</label><input type="number" className="form-control" required min={aberto.kmInicial ?? 0} value={chegada.kmFinal} onChange={e => setChegada({ ...chegada, kmFinal: e.target.value })} /></div><div className="col-md-3"><label className="form-label">Hora de chegada</label><input type="time" className="form-control" value={chegada.horaChegada} onChange={e => setChegada({ ...chegada, horaChegada: e.target.value })} /></div><div className="col-md-3"><button className="btn btn-primary" disabled={ocupado}><i className="bi bi-box-arrow-in-left"></i> Registrar chegada</button></div></div>}
      </Section></form></Card>}
      <div className="row g-3 mb-4">
        <div className="col-6 col-xl-2"><Kpi icon="bi-speedometer" label="Km atual" valor={numero(r.kmAtual)} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-fuel-pump" label="Combustível no ano" valor={moeda(r.combustivelAno)} extra={`${numero(r.litrosAno, 1)} L · ${r.qtdAbastecimentos} abast.`} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-wrench-adjustable" cor="warning" label="Manutenção no ano" valor={moeda(r.manutencaoAno)} extra={`${r.qtdManutencoes} serviço(s)`} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-signpost-2" cor="info" label="Km rodados no ano" valor={numero(r.kmRodadosAno)} extra={`${r.qtdDeslocamentos} saída(s)`} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-droplet-half" cor="success" label="Consumo médio" valor={r.consumoMedioKmPorLitro ? `${numero(r.consumoMedioKmPorLitro, 1)} km/L` : '—'} /></div>
        <div className="col-6 col-xl-2"><Kpi icon="bi-calendar-check" cor={r.revisaoVencida ? 'danger' : 'primary'} label="Próxima revisão" valor={r.proximaRevisaoKm ? `${numero(r.proximaRevisaoKm)} km` : r.proximaRevisaoData ? data(r.proximaRevisaoData) : '—'} extra={r.proximaRevisaoKm && r.proximaRevisaoData ? data(r.proximaRevisaoData) : undefined} /></div>
      </div>
      <Card className="mb-4"><Section icon="bi-card-list" titulo="Dados do veículo" semBorda>
        <div className="row g-3"><Dado l="Chassi" v={v.chassi} /><Dado l="Renavam" v={v.renavam} /><Dado l="Combustível padrão" v={rotulo(v.combustivelPadrao)} /><Dado l="Tombo patrimonial" v={v.numeroTombo ? <Link to={`/patrimonios/${v.patrimonioId}`}>{v.numeroTombo}</Link> : <Link to={`/patrimonios/${v.patrimonioId}`}>Ver bem</Link>} /><Dado l="Lotação" v={v.lotacaoNome ? `${v.lotacaoUpm} · ${v.lotacaoNome}` : ''} /><Dado l="Responsável" v={v.responsavelNome} /><Dado l="Data da compra" v={data(v.dataCompra)} /><Dado l="Valor" v={moeda(v.valorCompra)} /><Dado l="Conservação" v={rotulo(v.conservacao)} /><Dado l="Nota fiscal" v={v.notaFiscal} /></div>
        {v.observacao && <div className="mt-3"><div className="kpi-label">Observação</div><div className="obs-pre">{v.observacao}</div></div>}
      </Section></Card>
      <Card>
        <div className="p-3 border-bottom d-flex flex-wrap gap-2 align-items-center">
          <div className="form-section-title"><i className="bi bi-clock-history me-1"></i> Histórico</div>
          <div className="btn-group btn-group-sm ms-auto">{([['', 'Tudo'], ['DESLOCAMENTO', 'Saídas'], ['ABASTECIMENTO', 'Abastecimentos'], ['MANUTENCAO', 'Manutenções']] as const).map(([k, l]) => <button key={k} className={`btn ${filtro === k ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setFiltro(k)}>{l}</button>)}</div>
        </div>
        {hist.isLoading ? <Carregando /> : !historico.length ? <div className="empty-state"><div className="empty-state-icon"><i className="bi bi-clock-history"></i></div><div className="empty-state-title">Nenhum lançamento</div></div> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Data</th><th>Tipo</th><th>Lançamento</th><th className="text-end">Km</th><th className="text-end">Valor</th><th>Motorista</th><th>NF</th><th></th></tr></thead>
            <tbody>{historico.map(h => (
              <tr key={h.tipo + h.id} className={h.emAndamento ? 'row-na-rua' : ''}>
                <td className="text-nowrap">{data(h.data)} {hora(h.hora)}</td>
                <td><span className={`badge badge-plain ${tipoCor[h.tipo]}`}><i className={`bi ${tipoIcone[h.tipo]} me-1`}></i>{h.tipo === 'ABASTECIMENTO' ? 'Abastecimento' : h.tipo === 'MANUTENCAO' ? 'Manutenção' : 'Deslocamento'}</span></td>
                <td><div className="cell-stack"><span className="cell-primary">{h.titulo}{h.emAndamento && <span className="badge badge-plain text-bg-info ms-2">em andamento</span>}</span><span className="cell-secondary">{h.detalhe}</span></div></td>
                <td className="text-end">{numero(h.km)}</td><td className="text-end">{h.valor != null ? moeda(h.valor) : '—'}</td><td>{h.motorista || '—'}</td>
                <td>{h.possuiNotaFiscal && h.tipo !== 'DESLOCAMENTO' ? <button className="btn btn-sm btn-link p-0" onClick={() => abrirArquivo(api.frota.nfUrl(h.tipo === 'ABASTECIMENTO' ? 'abastecimentos' : 'manutencoes', h.id))}><i className="bi bi-paperclip"></i> NF</button> : '—'}</td>
                <td className="text-end text-nowrap">{gestor && <><Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={editarUrl(h)}><i className="bi bi-pencil"></i></Link>{' '}{temPerfil('ADMINISTRADOR') && <button className="btn btn-sm btn-outline-danger btn-icon" title="Excluir" disabled={ocupado} onClick={() => excluir(h)}><i className="bi bi-trash"></i></button>}</>}</td>
              </tr>))}</tbody>
          </table></div>)}
      </Card>
      {!v.ativo && temPerfil('ADMINISTRADOR') && <div className="small text-muted mt-2">Veículo inativo. <button className="btn btn-link btn-sm p-0" onClick={() => navigate('/frota')}>Voltar à lista</button></div>}
    </>
  )
}

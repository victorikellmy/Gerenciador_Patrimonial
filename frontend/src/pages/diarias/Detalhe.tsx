import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, Carregando, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { badgeStatusDiaria, data, dataHora, hojeIso, hora, moeda, numero, rotulo } from '../../utils/format'

function Dado({ l, v, col = 'col-md-4' }: { l: string; v: React.ReactNode; col?: string }) { return <div className={col}><div className="kpi-label">{l}</div><div className="fw-medium">{v || '—'}</div></div> }

export default function DiariaDetalhe() {
  const { id } = useParams()
  const did = Number(id)
  const { temPerfil } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const q = useQuery({ queryKey: ['diaria', id], queryFn: () => api.diarias.buscar(did) })
  const hist = useQuery({ queryKey: ['diaria-historico', id], queryFn: () => api.diarias.historico(did) })
  const [obs, setObs] = useState('')
  const [dataPg, setDataPg] = useState(hojeIso())
  const [ocupado, setOcupado] = useState(false)
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['diaria', id] }); qc.invalidateQueries({ queryKey: ['diaria-historico', id] }); qc.invalidateQueries({ queryKey: ['diarias'] }); qc.invalidateQueries({ queryKey: ['diarias-contagens'] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }) }
  const transicao = async (acao: 'confirmar' | 'deferir' | 'indeferir' | 'pagar') => {
    if (acao === 'indeferir' && !obs.trim()) { toast.aviso('Informe o motivo do indeferimento.'); return }
    setOcupado(true)
    try { await api.diarias.transicionar(did, acao, obs, acao === 'pagar' ? dataPg : undefined); toast.sucesso('Status atualizado.'); setObs(''); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) }
  }
  const excluir = async () => { if (!confirm('Excluir esta solicitação?')) return; try { await api.diarias.excluir(did); toast.sucesso('Solicitação excluída.'); invalidar(); navigate('/diarias') } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  if (q.isLoading) return <Carregando />
  if (q.error || !q.data) return <Erro erro={q.error ?? 'Não encontrada'} />
  const d = q.data
  const admin = temPerfil('ADMINISTRADOR')
  return (
    <>
      <PageHeader voltar="/diarias" titulo={`Diária #${d.id}`} subtitulo={<><Badge cor={badgeStatusDiaria[d.status]}>{rotulo(d.status)}</Badge> <span className="ms-2">solicitada em {data(d.dataSolicitacao)} por {d.solicitante}</span></>}
        acoes={<>
          <button className="btn btn-secondary" onClick={() => abrirArquivo(api.diarias.pdfUrl(d.id))}><i className="bi bi-file-earmark-pdf"></i> Solicitação em PDF</button>
          {d.editavel && temPerfil('ADMINISTRADOR', 'FISCAL') && <Link className="btn btn-secondary" to={`/diarias/${d.id}/editar`}><i className="bi bi-pencil"></i> Editar</Link>}
          {d.editavel && admin && <button className="btn btn-outline-danger btn-icon" title="Excluir" onClick={excluir}><i className="bi bi-trash"></i></button>}
        </>} />
      <div className="row g-3">
        <div className="col-lg-8">
          <Card>
            <Section icon="bi-person-vcard" titulo="Profissional">
              <div className="row g-3"><Dado l="Nome" v={d.profissionalNome} /><Dado l="Matrícula" v={d.profissionalMatricula} /><Dado l="Cargo / nível" v={d.profissionalNivel} /><Dado l="Lotação" v={d.profissionalLotacao} /><Dado l="CPF" v={d.profissionalCpf} /><Dado l="RG" v={d.profissionalRg ? `${d.profissionalRg} ${d.profissionalOrgaoExpedidor ?? ''}` : ''} /><Dado l="Dados bancários" v={d.profissionalDadosBancarios} col="col-12" /></div>
            </Section>
            <Section icon="bi-geo-alt" titulo="Viagem">
              <div className="row g-3"><Dado l="Itinerário" v={d.itinerario} col="col-12" /><Dado l="Partida" v={`${data(d.partida)} ${d.horaSaida ? hora(d.horaSaida) : ''}`} /><Dado l="Retorno" v={`${data(d.retorno)} ${d.horaChegada ? hora(d.horaChegada) : ''}`} /><Dado l="Transporte" v={rotulo(d.meioTransporte)} /><Dado l="Recurso" v={rotulo(d.recurso)} /><Dado l="Finalidade" v={<span className="obs-pre">{d.finalidade}</span>} col="col-12" /></div>
            </Section>
            <Section icon="bi-cash-coin" titulo="Valores" semBorda>
              <div className="row g-3"><Dado l="Nº de diárias" v={numero(d.numeroDiarias, 1)} /><Dado l="Valor unitário" v={moeda(d.valorUnitario)} /><Dado l="Total" v={<span className="kpi-value">{moeda(d.valorTotal)}</span>} />{d.dataPagamento && <Dado l="Pago em" v={data(d.dataPagamento)} />}{d.motivoIndeferimento && <Dado l="Motivo do indeferimento" v={d.motivoIndeferimento} col="col-12" />}</div>
            </Section>
          </Card>
        </div>
        <div className="col-lg-4">
          <Card className="mb-3"><Section icon="bi-check2-square" titulo="Ações" semBorda>
            {d.status === 'SOLICITADO' && (admin ? <div className="d-grid gap-2"><input className="form-control" placeholder="Observação (opcional)" value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-primary" disabled={ocupado} onClick={() => transicao('confirmar')}><i className="bi bi-check2"></i> Confirmar solicitação</button></div> : <div className="text-muted small">Aguardando confirmação da administração.</div>)}
            {d.status === 'CONFIRMADO' && (temPerfil('ADMINISTRADOR', 'PRESIDENTE') ? <div className="d-grid gap-2"><input className="form-control" placeholder="Observação / motivo" value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-success" disabled={ocupado} onClick={() => transicao('deferir')}><i className="bi bi-hand-thumbs-up"></i> Deferir</button><button className="btn btn-outline-danger" disabled={ocupado} onClick={() => transicao('indeferir')}><i className="bi bi-hand-thumbs-down"></i> Indeferir</button></div> : <div className="text-muted small">Aguardando decisão da presidência.</div>)}
            {d.status === 'DEFERIDO' && (temPerfil('ADMINISTRADOR', 'FINANCEIRO') ? <div className="d-grid gap-2"><label className="form-label mb-0">Data do pagamento</label><input type="date" className="form-control" value={dataPg} onChange={e => setDataPg(e.target.value)} /><input className="form-control" placeholder="Observação (opcional)" value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-success" disabled={ocupado} onClick={() => transicao('pagar')}><i className="bi bi-cash"></i> Registrar pagamento</button></div> : <div className="text-muted small">Aguardando pagamento pelo financeiro.</div>)}
            {d.status === 'PAGO' && <div className="text-success small"><i className="bi bi-check-circle-fill"></i> Diária paga em {data(d.dataPagamento)}.</div>}
            {d.status === 'INDEFERIDO' && <div className="text-danger small"><i className="bi bi-x-circle-fill"></i> Indeferida.</div>}
          </Section></Card>
          <Card><Section icon="bi-clock-history" titulo="Histórico" semBorda>
            {!hist.data?.length ? <div className="text-muted small">Sem registros.</div> : <div className="d-grid gap-2">{hist.data.map(h => <div key={h.id} className="border-start ps-3"><div className="small text-muted">{dataHora(h.instante)} · {h.usuario}</div><div className="small">{h.deStatus ? `${rotulo(h.deStatus)} → ` : ''}<strong>{rotulo(h.paraStatus)}</strong></div>{h.observacao && <div className="small fst-italic">{h.observacao}</div>}</div>)}</div>}
          </Section></Card>
        </div>
      </div>
    </>
  )
}

import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, Carregando, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { badgeStatusDiaria, data, dataHora, hojeIso, hora, moeda, numero, rotulo } from '../../utils/format'

function Dado({ l, v, col = 'col-md-4' }: { l: string; v: React.ReactNode; col?: string }) { return <div className={col}><div className="kpi-label">{l}</div><div className="fw-medium">{v || '—'}</div></div> }

const duracao = (min: number) => `${Math.floor(min / 60)}h ${min % 60}min`

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
  const excluir = async () => { if (!confirm('Excluir este requerimento?')) return; try { await api.diarias.excluir(did); toast.sucesso('Requerimento excluído.'); invalidar(); navigate('/diarias') } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  if (q.isLoading) return <Carregando />
  if (q.error || !q.data) return <Erro erro={q.error ?? 'Não encontrada'} />
  const d = q.data
  const admin = temPerfil('ADMINISTRADOR')
  const c = d.calculo
  const temConta = d.banco || d.agencia || d.contaCorrente || d.pix
  return (
    <>
      <PageHeader voltar="/diarias" titulo={`Diária nº ${d.numeroRequerimento ?? d.id}`} subtitulo={<><Badge cor={badgeStatusDiaria[d.status]}>{rotulo(d.status)}</Badge> <span className="ms-2">requerida em {data(d.dataSolicitacao)}, registrada por {d.solicitante}</span></>}
        acoes={<>
          <button className="btn btn-primary" onClick={() => abrirArquivo(api.diarias.pdfUrl(d.id))}><i className="bi bi-file-earmark-pdf"></i> Requerimento em PDF</button>
          <button className="btn btn-secondary" title="Resumo interno com o histórico de aprovação" onClick={() => abrirArquivo(api.diarias.extratoUrl(d.id))}><i className="bi bi-clock-history"></i> Extrato</button>
          {d.editavel && temPerfil('ADMINISTRADOR', 'FISCAL') && <Link className="btn btn-secondary" to={`/diarias/${d.id}/editar`}><i className="bi bi-pencil"></i> Editar</Link>}
          {d.editavel && admin && <button className="btn btn-outline-danger btn-icon" title="Excluir" onClick={excluir}><i className="bi bi-trash"></i></button>}
        </>} />
      {d.status === 'SOLICITADO' && (
        <div className="alert alert-info"><i className="bi bi-send"></i><div>Próximo passo: abra o <strong>Requerimento em PDF</strong>, salve o arquivo e envie pelo SGD para o parecer do Diretor Presidente. O documento sai no modelo do Anexo II da Portaria 001/2019.</div></div>
      )}
      <div className="row g-3">
        <div className="col-lg-8">
          <Card>
            <Section icon="bi-person-vcard" titulo="Profissional">
              <div className="row g-3"><Dado l="Nome" v={d.profissionalNome} /><Dado l="Matrícula funcional" v={d.profissionalMatricula} /><Dado l="Cargo, posto ou função" v={d.profissionalCargo ?? d.profissionalNivel} /><Dado l="Lotação" v={d.profissionalLotacao} /><Dado l="CPF" v={d.profissionalCpf} /><Dado l="RG / órgão expedidor" v={[d.profissionalRg, d.profissionalOrgaoExpedidor].filter(Boolean).join(' · ')} />
                {temConta ? <><Dado l="Banco" v={d.banco} col="col-md-3" /><Dado l="Agência" v={d.agencia} col="col-md-3" /><Dado l="Conta corrente" v={d.contaCorrente} col="col-md-3" /><Dado l="PIX" v={d.pix} col="col-md-3" /></> : <Dado l="Dados bancários" v={d.profissionalDadosBancarios} col="col-12" />}</div>
            </Section>
            <Section icon="bi-geo-alt" titulo="Missão e deslocamento">
              <div className="row g-3"><Dado l="Origem" v={d.origem} col="col-md-6" /><Dado l="Destino / itinerário" v={d.itinerario} col="col-md-6" /><Dado l="Saída" v={`${data(d.partida)} ${d.horaSaida ? hora(d.horaSaida) : ''}`} /><Dado l="Retorno à origem" v={`${data(d.retorno)} ${d.horaChegada ? hora(d.horaChegada) : ''}`} /><Dado l="Duração" v={c ? duracao(c.duracaoMin) : ''} /><Dado l="Meio de transporte" v={rotulo(d.meioTransporte)} /><Dado l="Fonte do recurso" v={rotulo(d.recurso)} /><Dado l="Finalidade da viagem" v={<span className="obs-pre">{d.finalidade}</span>} col="col-12" /></div>
            </Section>
            <Section icon="bi-calculator" titulo="Apuração da quantidade e do valor" semBorda>
              <div className="row g-3"><Dado l="Tipo de diária" v={d.tipoDiaria ? rotulo(d.tipoDiaria) : ''} col="col-md-3" /><Dado l="Quantidade de diárias" v={numero(d.numeroDiarias, 1)} col="col-md-3" /><Dado l="Valor unitário" v={moeda(d.valorUnitario)} col="col-md-3" /><Dado l="Valor total" v={<span className="kpi-value">{moeda(d.valorTotal)}</span>} col="col-md-3" />{d.dataPagamento && <Dado l="Pago em" v={data(d.dataPagamento)} />}{d.motivoIndeferimento && <Dado l="Motivo do indeferimento" v={d.motivoIndeferimento} col="col-12" />}</div>
              {c && (
                <div className="table-responsive table-livre mt-3"><table className="table table-compacta mb-0">
                  <thead><tr><th>Memória de cálculo</th><th>Critério aplicado</th><th className="text-end">Diárias</th></tr></thead>
                  <tbody>
                    <tr><td>Períodos completos de 24 horas</td><td>{c.integrais} período(s) × 1 diária</td><td className="text-end">{numero(c.integrais, 1)}</td></tr>
                    <tr><td>Alimentação adicional</td><td>{c.restoMin === 0 ? 'Não há intervalo restante.' : c.refeicoes.length ? `${c.refeicoes.map(r => dataHora(r)).join('; ')} (limite de 0,5)` : 'Sem coincidência com 12h ou 18h no intervalo restante.'}</td><td className="text-end">{numero(c.alimentacao, 1)}</td></tr>
                  </tbody>
                </table></div>
              )}
              {c && Number(c.quantidade) !== Number(d.numeroDiarias) && <div className="small text-muted mt-2">A quantidade registrada ({numero(d.numeroDiarias, 1)}) é anterior à regra de apuração automática; pela regra atual seriam {numero(c.quantidade, 1)}.</div>}
              {c?.avisoPortaria && <div className="alert alert-warning mt-3 mb-0 small"><i className="bi bi-exclamation-triangle-fill"></i> {c.avisoPortaria}</div>}
            </Section>
          </Card>
        </div>
        <div className="col-lg-4">
          <Card className="mb-3"><Section icon="bi-check2-square" titulo="Ações" semBorda>
            {d.status === 'SOLICITADO' && (admin ? <div className="d-grid gap-2"><input className="form-control" placeholder="Observação (opcional)" value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-primary" disabled={ocupado} onClick={() => transicao('confirmar')}><i className="bi bi-check2"></i> Confirmar solicitação</button></div> : <div className="text-muted small">Aguardando confirmação da administração.</div>)}
            {d.status === 'CONFIRMADO' && (temPerfil('ADMINISTRADOR', 'PRESIDENTE') ? <div className="d-grid gap-2"><textarea className="form-control" rows={3} maxLength={250} placeholder="Fundamentação / observações. Em branco, a autorização usa o texto padrão do art. 1º e 4º, I, da Portaria 001/2019." value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-success" disabled={ocupado} onClick={() => transicao('deferir')}><i className="bi bi-hand-thumbs-up"></i> Autorizar (deferir)</button><button className="btn btn-outline-danger" disabled={ocupado} onClick={() => transicao('indeferir')}><i className="bi bi-hand-thumbs-down"></i> Não autorizar (indeferir)</button></div> : <div className="text-muted small">Aguardando decisão da presidência.</div>)}
            {d.status === 'DEFERIDO' && (temPerfil('ADMINISTRADOR', 'FINANCEIRO') ? <div className="d-grid gap-2"><label className="form-label mb-0">Data do pagamento</label><input type="date" className="form-control" value={dataPg} onChange={e => setDataPg(e.target.value)} /><input className="form-control" placeholder="Observação (opcional)" value={obs} onChange={e => setObs(e.target.value)} /><button className="btn btn-success" disabled={ocupado} onClick={() => transicao('pagar')}><i className="bi bi-cash"></i> Registrar pagamento</button></div> : <div className="text-muted small">Aguardando pagamento pelo financeiro.</div>)}
            {d.status === 'PAGO' && <div className="text-success small"><i className="bi bi-check-circle-fill"></i> Diária paga em {data(d.dataPagamento)}.</div>}
            {d.status === 'INDEFERIDO' && <div className="text-danger small"><i className="bi bi-x-circle-fill"></i> Não autorizada.</div>}
          </Section></Card>
          <Card><Section icon="bi-clock-history" titulo="Histórico" semBorda>
            {!hist.data?.length ? <div className="text-muted small">Sem registros.</div> : <div className="d-grid gap-2">{hist.data.map(h => <div key={h.id} className="border-start ps-3"><div className="small text-muted">{dataHora(h.instante)} · {h.usuario}</div><div className="small">{h.deStatus ? `${rotulo(h.deStatus)} → ` : ''}<strong>{rotulo(h.paraStatus)}</strong></div>{h.observacao && <div className="small fst-italic">{h.observacao}</div>}</div>)}</div>}
          </Section></Card>
        </div>
      </div>
    </>
  )
}

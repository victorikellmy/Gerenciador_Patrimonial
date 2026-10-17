import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { data, dataHora, moeda, rotulo } from '../../utils/format'

function Dado({ l, v }: { l: string; v: React.ReactNode }) { return <div className="col-md-4"><div className="kpi-label">{l}</div><div className="fw-medium">{v || '—'}</div></div> }

export default function PendenciaDetalhe() {
  const { id } = useParams()
  const pid = Number(id)
  const { temPerfil } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const q = useQuery({ queryKey: ['pendencia', id], queryFn: () => api.pendencias.buscar(pid) })
  const [motivo, setMotivo] = useState('')
  const [descartando, setDescartando] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['pendencia', id] }); qc.invalidateQueries({ queryKey: ['pendencias'] }); qc.invalidateQueries({ queryKey: ['pendencias-contagem'] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }) }
  if (q.isLoading) return <Carregando />
  if (q.error || !q.data) return <Erro erro={q.error ?? 'Não encontrada'} />
  const p = q.data
  const concluir = async () => { if (!confirm('Concluir esta pendência sem patrimoniar as unidades restantes?')) return; setOcupado(true); try { await api.pendencias.concluir(pid); toast.sucesso('Pendência concluída.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) } }
  const descartar = async () => { setOcupado(true); try { await api.pendencias.descartar(pid, motivo); toast.sucesso('Pendência descartada.'); invalidar(); navigate('/pendencias') } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) } }
  return (
    <>
      <PageHeader voltar="/pendencias" titulo={`Pendência #${p.id}`} subtitulo={<><span className={`badge badge-plain ${p.status === 'PENDENTE' ? 'text-bg-warning' : p.status === 'CONCLUIDA' ? 'text-bg-success' : 'text-bg-secondary'}`}>{rotulo(p.status)}</span> <span className="ms-2">{p.origem} · compra {p.compraIdOrigem}</span></>}
        acoes={p.status === 'PENDENTE' && <>
          {p.quantidadeRestante > 0 && <Link className="btn btn-primary" to={`/pendencias/${p.id}/patrimoniar`}><i className="bi bi-box-seam"></i> Patrimoniar próxima unidade ({p.quantidadeRestante} restante{p.quantidadeRestante > 1 ? 's' : ''})</Link>}
          <button className="btn btn-secondary" disabled={ocupado} onClick={concluir}><i className="bi bi-check2"></i> Concluir</button>
          {temPerfil('ADMINISTRADOR') && <button className="btn btn-outline-danger" onClick={() => setDescartando(d => !d)}><i className="bi bi-x-octagon"></i> Descartar</button>}
        </>} />
      {descartando && <Card className="mb-3"><Section icon="bi-x-octagon" titulo="Descartar pendência" desc="Informe o motivo. A pendência não poderá mais ser patrimoniada." semBorda>
        <div className="row g-2"><div className="col-md-9"><input className="form-control" placeholder="Motivo" value={motivo} onChange={e => setMotivo(e.target.value)} /></div><div className="col-md-3 d-flex gap-2 justify-content-end"><button className="btn btn-secondary" onClick={() => setDescartando(false)}>Cancelar</button><button className="btn btn-danger" disabled={!motivo.trim() || ocupado} onClick={descartar}>Descartar</button></div></div></Section></Card>}
      <div className="row g-3">
        <div className="col-lg-8">
          <Card>
            <Section icon="bi-file-text" titulo="Documento de origem">
              <div className="row g-3"><Dado l="Documento" v={p.numeroDocumento} /><Dado l="SGD" v={p.numeroSgd} /><Dado l="Solicitante" v={p.solicitanteDocumento} /><div className="col-12"><div className="kpi-label">Assunto</div><div>{p.assunto || '—'}</div></div></div>
            </Section>
            <Section icon="bi-receipt" titulo="Compra e recebimento">
              <div className="row g-3"><Dado l="Fornecedor" v={p.fornecedor} /><Dado l="Nota fiscal" v={p.numeroNotaFiscal} /><Dado l="Valor total" v={moeda(p.valorTotal)} /><Dado l="Data da compra" v={data(p.dataCompra)} /><Dado l="Recebido em" v={dataHora(p.dataRecebimento)} /><Dado l="Retirado por" v={p.retiradoPor} /><Dado l="Setor destino" v={p.setorDestino} /><Dado l="Centro de custo" v={p.setorDestinoCentroCusto} /><Dado l="Registrado por" v={p.registradoPor} /></div>
              {p.observacao && <div className="mt-3"><div className="kpi-label">Observação</div><div className="obs-pre">{p.observacao}</div></div>}
            </Section>
            <Section icon="bi-list-check" titulo="Itens" semBorda>
              <div className="table-responsive table-livre"><table className="table table-sm table-compacta mb-0"><thead><tr><th>Descrição</th><th>SKU</th><th className="text-end">Qtd.</th><th className="text-end">Valor unit.</th></tr></thead>
                <tbody>{p.itens.map((i, k) => <tr key={k}><td>{i.descricao}</td><td>{i.codigoSku || '—'}</td><td className="text-end">{i.quantidade ?? 1}</td><td className="text-end">{moeda(i.valorUnitario)}</td></tr>)}</tbody></table></div>
            </Section>
          </Card>
        </div>
        <div className="col-lg-4">
          <Card className="mb-3"><Section icon="bi-bar-chart-steps" titulo="Progresso" semBorda>
            <div className="kpi-value">{p.quantidadePatrimoniada} / {p.quantidadeTotal}</div><div className="small text-muted">unidades patrimoniadas · {p.quantidadeRestante} restante(s)</div>
            <div className="progress mt-2" style={{ height: 8 }}><div className="progress-bar" style={{ width: `${p.quantidadeTotal ? 100 * p.quantidadePatrimoniada / p.quantidadeTotal : 0}%` }}></div></div>
            {p.patrimonioId && <div className="mt-3 small">Último bem: <Link to={`/patrimonios/${p.patrimonioId}`}>#{p.patrimonioId}</Link></div>}
            {p.concluidaEm && <div className="mt-2 small text-muted">Concluída por {p.concluidaPor} em {dataHora(p.concluidaEm)}</div>}
          </Section></Card>
          <Card><Section icon="bi-paperclip" titulo="Nota fiscal" semBorda>
            {p.possuiNotaFiscal ? <a className="btn btn-secondary w-100" href={`/api/pendencias/${p.id}/nota-fiscal`} target="_blank" rel="noreferrer"><i className="bi bi-file-earmark-pdf"></i> {p.nfNomeOriginal || 'Abrir nota fiscal'}</a> : <div className="text-muted small">O Almoxarifado não enviou a nota fiscal.</div>}
          </Section></Card>
        </div>
      </div>
    </>
  )
}

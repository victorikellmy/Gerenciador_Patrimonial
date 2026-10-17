import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { ItemLevantamento, ResultadoConferencia } from '../../api/types'
import { Card, Carregando, Erro, PageHeader, useToast } from '../../components/ui'
import { rotulo, CONSERVACOES } from '../../utils/format'

const corResultado: Record<string, string> = { OK: 'text-bg-success', AVARIADO: 'text-bg-warning', NAO_LOCALIZADO: 'text-bg-danger', PENDENTE: 'text-bg-secondary' }

function LinhaItem({ item, lid, lotacaoId, aberto, onDone }: { item: ItemLevantamento; lid: number; lotacaoId: number; aberto: boolean; onDone: () => void }) {
  const toast = useToast()
  const [modo, setModo] = useState<'' | 'AVARIADO' | 'NAO_LOCALIZADO'>('')
  const [conservacao, setConservacao] = useState<string>(item.conservacaoAtual ?? '')
  const [texto, setTexto] = useState('')
  const [foto, setFoto] = useState<File | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const conferir = async (resultado: ResultadoConferencia) => {
    if (resultado === 'AVARIADO' && !texto.trim()) { toast.aviso('Descreva a avaria.'); return }
    setOcupado(true)
    try {
      await api.levantamentos.conferir(lid, item.id, { resultado, conservacaoVerificada: conservacao || undefined, descricaoAvaria: resultado === 'AVARIADO' ? texto : undefined, observacao: resultado !== 'AVARIADO' ? texto || undefined : undefined, lotacaoContextoId: lotacaoId }, foto)
      setModo(''); setTexto(''); setFoto(null); onDone()
    } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao conferir.') } finally { setOcupado(false) }
  }
  const desfazer = async () => { setOcupado(true); try { await api.levantamentos.desfazer(lid, item.id); onDone() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) } }
  const pendente = item.resultado === 'PENDENTE'
  const deOutroLocal = item.lotacaoId !== lotacaoId
  return (
    <div className={`p-3 border-bottom ${pendente ? '' : 'bg-body-tertiary'}`}>
      <div className="d-flex flex-wrap align-items-start gap-3">
        <div className="flex-grow-1 min-w-0" style={{ minWidth: 240 }}>
          <div><span className="chip-tombo me-2">{item.numeroTombo || 'S/T'}</span><strong>{item.descricao}</strong></div>
          <div className="small text-muted">{item.categoria}{item.subcategoria ? ` · ${item.subcategoria}` : ''} · {rotulo(item.situacao)} · conservação {rotulo(item.conservacaoAtual)}{item.responsavelNome ? ` · ${item.responsavelNome}` : ''}</div>
          {deOutroLocal && <div className="small text-warning"><i className="bi bi-geo"></i> Lotado em {item.lotacaoUpm} · {item.lotacaoNome}</div>}
          {!pendente && <div className="small mt-1"><span className={`badge badge-plain ${corResultado[item.resultado]}`}>{rotulo(item.resultado)}</span> {item.conservacaoVerificada && <span className="ms-1">conservação {rotulo(item.conservacaoVerificada)}</span>} {item.descricaoAvaria && <span className="ms-1">· {item.descricaoAvaria}</span>} {item.observacao && <span className="ms-1">· {item.observacao}</span>} {item.fotoAtualId && <a className="ms-1" href={`/api/anexos/${item.fotoAtualId}/download`} target="_blank" rel="noreferrer"><i className="bi bi-image"></i> foto</a>} <span className="text-muted ms-1">por {item.verificadoPor}</span></div>}
        </div>
        {aberto && pendente && (
          <div className="d-flex flex-wrap align-items-center gap-2">
            <select className="form-select form-select-sm" style={{ width: 150 }} value={conservacao} onChange={e => setConservacao(e.target.value)} title="Conservação verificada"><option value="">Conservação</option>{CONSERVACOES.map(c => <option key={c} value={c}>{rotulo(c)}</option>)}</select>
            <label className="btn btn-sm btn-secondary btn-check-item mb-0" title="Foto"><i className={`bi ${foto ? 'bi-camera-fill text-success' : 'bi-camera'}`}></i><input type="file" accept="image/*" capture="environment" hidden onChange={e => setFoto(e.target.files?.[0] ?? null)} /></label>
            <button className="btn btn-sm btn-success btn-check-item" disabled={ocupado} onClick={() => conferir('OK')}><i className="bi bi-check-lg"></i> OK</button>
            <button className={`btn btn-sm ${modo === 'AVARIADO' ? 'btn-warning' : 'btn-outline-warning'} btn-check-item`} disabled={ocupado} onClick={() => setModo(modo === 'AVARIADO' ? '' : 'AVARIADO')}><i className="bi bi-exclamation-triangle"></i> Avaria</button>
            <button className={`btn btn-sm ${modo === 'NAO_LOCALIZADO' ? 'btn-danger' : 'btn-outline-danger'} btn-check-item`} disabled={ocupado} onClick={() => setModo(modo === 'NAO_LOCALIZADO' ? '' : 'NAO_LOCALIZADO')}><i className="bi bi-question-lg"></i> Não localizado</button>
          </div>)}
        {aberto && !pendente && <button className="btn btn-sm btn-outline-secondary" disabled={ocupado} onClick={desfazer}><i className="bi bi-arrow-counterclockwise"></i> Desfazer</button>}
      </div>
      {modo && (
        <div className="d-flex flex-wrap gap-2 mt-2 align-items-center">
          <input className="form-control form-control-sm flex-grow-1" style={{ minWidth: 260 }} placeholder={modo === 'AVARIADO' ? 'Descreva a avaria (obrigatório)' : 'Observação (opcional)'} value={texto} onChange={e => setTexto(e.target.value)} />
          <button className={`btn btn-sm ${modo === 'AVARIADO' ? 'btn-warning' : 'btn-danger'}`} disabled={ocupado} onClick={() => conferir(modo)}>Confirmar {modo === 'AVARIADO' ? 'avaria' : 'não localizado'}</button>
        </div>)}
    </div>
  )
}

export default function LevantamentoChecklist() {
  const { id, lotacaoId } = useParams()
  const lid = Number(id); const lot = Number(lotacaoId)
  const qc = useQueryClient()
  const toast = useToast()
  const lev = useQuery({ queryKey: ['levantamento', id], queryFn: () => api.levantamentos.buscar(lid) })
  const itens = useQuery({ queryKey: ['levantamento-itens', id, lotacaoId], queryFn: () => api.levantamentos.itensDoLocal(lid, lot) })
  const [somentePendentes, setSomentePendentes] = useState(true)
  const [busca, setBusca] = useState('')
  const [tombo, setTombo] = useState('')
  const [localizando, setLocalizando] = useState(false)
  const recarregar = () => { qc.invalidateQueries({ queryKey: ['levantamento-itens', id, lotacaoId] }); qc.invalidateQueries({ queryKey: ['levantamento', id] }); qc.invalidateQueries({ queryKey: ['levantamento-guia', id] }); qc.invalidateQueries({ queryKey: ['levantamento-ocorrencias', id] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }) }
  const localizar = async () => {
    if (!tombo.trim()) return
    setLocalizando(true)
    try { const it = await api.levantamentos.localizar(lid, lot, tombo.trim()); toast.sucesso(`Bem ${it.numeroTombo ?? it.descricao} registrado como encontrado neste local.`); setTombo(''); recarregar() }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Bem não encontrado.') } finally { setLocalizando(false) }
  }
  const lista = useMemo(() => (itens.data ?? []).filter(i => (!somentePendentes || i.resultado === 'PENDENTE') && (!busca || `${i.numeroTombo ?? ''} ${i.descricao}`.toLowerCase().includes(busca.toLowerCase()))), [itens.data, somentePendentes, busca])
  if (lev.isLoading || itens.isLoading) return <Carregando />
  if (lev.error || itens.error) return <Erro erro={lev.error ?? itens.error} />
  const primeiro = itens.data?.[0]
  const nomeLocal = primeiro ? `${primeiro.lotacaoId === lot ? primeiro.lotacaoUpm + ' · ' + primeiro.lotacaoNome : primeiro.lotacaoEncontradaUpm + ' · ' + primeiro.lotacaoEncontradaNome}` : `Local ${lot}`
  const total = itens.data?.length ?? 0, pend = itens.data?.filter(i => i.resultado === 'PENDENTE').length ?? 0
  const aberto = lev.data?.status === 'ABERTO'
  return (
    <>
      <PageHeader voltar={`/levantamentos/${lid}`} titulo={nomeLocal} subtitulo={`Levantamento ${lev.data?.ano} · ${total - pend} de ${total} conferidos`} />
      <Card className="mb-3">
        <div className="p-3 d-flex flex-wrap gap-2 align-items-center">
          <input className="form-control form-control-sm" style={{ maxWidth: 260 }} placeholder="Buscar por tombo ou descrição" value={busca} onChange={e => setBusca(e.target.value)} />
          <div className="btn-group btn-group-sm"><button className={`btn ${somentePendentes ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setSomentePendentes(true)}>Pendentes ({pend})</button><button className={`btn ${!somentePendentes ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setSomentePendentes(false)}>Todos ({total})</button></div>
          {aberto && <div className="input-group input-group-sm ms-auto" style={{ maxWidth: 320 }}><span className="input-group-text"><i className="bi bi-search"></i></span><input className="form-control" placeholder="Tombo de bem de outro local" value={tombo} onChange={e => setTombo(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') localizar() }} /><button className="btn btn-secondary" disabled={localizando} onClick={localizar}>Localizar aqui</button></div>}
        </div>
      </Card>
      <Card>
        {!lista.length ? <div className="empty-state"><div className="empty-state-icon"><i className="bi bi-check2-circle"></i></div><div className="empty-state-title">{somentePendentes ? 'Nenhum bem pendente neste local' : 'Nenhum bem neste local'}</div></div> : lista.map(i => <LinhaItem key={i.id} item={i} lid={lid} lotacaoId={lot} aberto={!!aberto} onDone={recarregar} />)}
      </Card>
    </>
  )
}

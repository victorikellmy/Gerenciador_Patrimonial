import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, Erro, Kpi, PageHeader, Section, useToast } from '../../components/ui'
import { dataHora, rotulo } from '../../utils/format'

const corResultado: Record<string, string> = { OK: 'text-bg-success', AVARIADO: 'text-bg-warning', NAO_LOCALIZADO: 'text-bg-danger', PENDENTE: 'text-bg-secondary' }

export default function LevantamentoGuia() {
  const { id } = useParams()
  const lid = Number(id)
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const lev = useQuery({ queryKey: ['levantamento', id], queryFn: () => api.levantamentos.buscar(lid) })
  const guia = useQuery({ queryKey: ['levantamento-guia', id], queryFn: () => api.levantamentos.guia(lid) })
  const ocorrencias = useQuery({ queryKey: ['levantamento-ocorrencias', id], queryFn: () => api.levantamentos.ocorrencias(lid) })
  const [busca, setBusca] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['levantamento', id] }); qc.invalidateQueries({ queryKey: ['levantamento-guia', id] }); qc.invalidateQueries({ queryKey: ['levantamento-ocorrencias', id] }); qc.invalidateQueries({ queryKey: ['levantamentos'] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }) }
  const acao = async (fn: () => Promise<unknown>, msg: string) => { setOcupado(true); try { await fn(); toast.sucesso(msg); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setOcupado(false) } }

  if (lev.isLoading || guia.isLoading) return <Carregando />
  if (lev.error || !lev.data) return <Erro erro={lev.error ?? 'Não encontrado'} />
  const l = lev.data
  const aberto = l.status === 'ABERTO'
  const pct = l.total ? Math.round(100 * (l.total - l.pendentes) / l.total) : 0
  const upms = (guia.data ?? []).map(u => ({ ...u, locais: u.locais.filter(x => !busca || `${x.upm} ${x.nome}`.toLowerCase().includes(busca.toLowerCase())) })).filter(u => u.locais.length)

  return (
    <>
      <PageHeader voltar="/levantamentos" titulo={`Levantamento ${l.ano}`} subtitulo={<>{l.descricao} · <span className={`badge badge-plain ${aberto ? 'text-bg-info' : 'text-bg-success'}`}>{aberto ? 'Em andamento' : 'Concluído'}</span></>}
        acoes={<>
          <button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.levantamentoUrl(lid, 'xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button>
          <button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.levantamentoUrl(lid, 'pdf'))}><i className="bi bi-file-earmark-pdf"></i> PDF</button>
          {temPerfil('ADMINISTRADOR') && aberto && <button className="btn btn-primary" disabled={ocupado} onClick={() => { if (confirm(l.pendentes ? `Ainda há ${l.pendentes} bem(ns) pendente(s). Concluir mesmo assim?` : 'Concluir o levantamento?')) acao(() => api.levantamentos.concluir(lid), 'Levantamento concluído.') }}><i className="bi bi-check2-all"></i> Concluir</button>}
          {temPerfil('ADMINISTRADOR') && !aberto && <button className="btn btn-outline-secondary" disabled={ocupado} onClick={() => { if (confirm('Reabrir o levantamento?')) acao(() => api.levantamentos.reabrir(lid), 'Levantamento reaberto.') }}><i className="bi bi-arrow-counterclockwise"></i> Reabrir</button>}
        </>} />
      <div className="row g-3 mb-4">
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-clipboard2-check" label="Progresso" valor={`${pct}%`} extra={`${l.total - l.pendentes} de ${l.total} bens conferidos`} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-check-circle" cor="success" label="OK" valor={l.ok} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-exclamation-triangle" cor="warning" label="Avariados" valor={l.avariados} /></div>
        <div className="col-sm-6 col-xl-3"><Kpi icon="bi-question-circle" cor="danger" label="Não localizados" valor={l.naoLocalizados} extra={l.divergenciasLocal ? `${l.divergenciasLocal} encontrado(s) em outro local` : undefined} /></div>
      </div>
      <Card className="mb-4">
        <Section icon="bi-geo-alt" titulo="Locais a conferir" desc="Escolha a sala/setor para abrir o checklist." semBorda>
          <input className="form-control form-control-sm mb-3" placeholder="Filtrar por UPM ou local..." value={busca} onChange={e => setBusca(e.target.value)} />
          {upms.map(u => (
            <div key={u.upm} className="mb-3">
              <div className="sidebar-section-title px-0">{u.upm}</div>
              <div className="row g-2">
                {u.locais.map(x => { const p = x.total ? Math.round(100 * (x.total - x.pendentes) / x.total) : 100; return (
                  <div className="col-md-6 col-xl-4" key={x.lotacaoId}>
                    <Link to={`/levantamentos/${lid}/locais/${x.lotacaoId}`} className="card card-panel h-100 text-decoration-none p-3">
                      <div className="d-flex justify-content-between align-items-start gap-2"><strong>{x.nome}</strong><span className={`badge badge-plain ${x.pendentes ? 'text-bg-warning' : 'text-bg-success'}`}>{x.pendentes ? `${x.pendentes} pend.` : 'completo'}</span></div>
                      <div className="progress my-2" style={{ height: 6 }}><div className="progress-bar" style={{ width: `${p}%` }}></div></div>
                      <div className="small text-muted">{x.total} bens · {x.ok} ok · {x.avariados} avar. · {x.naoLocalizados} n/loc.{x.encontradosAqui ? ` · ${x.encontradosAqui} de outro local` : ''}</div>
                    </Link>
                  </div>) })}
              </div>
            </div>))}
        </Section>
      </Card>
      <Card>
        <Section icon="bi-exclamation-diamond" titulo="Ocorrências" desc="Avarias, bens não localizados e encontrados em local diferente." semBorda>
          {!ocorrencias.data?.length ? <div className="text-muted small">Nenhuma ocorrência registrada.</div> : (
            <div className="table-responsive table-livre lista-ocorrencias"><table className="table table-compacta mb-0">
              <thead><tr><th>Bem</th><th>Local esperado</th><th>Encontrado em</th><th>Resultado</th><th>Conservação</th><th>Detalhe</th><th>Foto</th><th>Por</th><th></th></tr></thead>
              <tbody>{ocorrencias.data.map(i => (
                <tr key={i.id}>
                  <td><span className="chip-tombo me-1">{i.numeroTombo || 'S/T'}</span><Link to={`/patrimonios/${i.patrimonioId}`}>{i.descricao}</Link></td>
                  <td>{i.lotacaoUpm} · {i.lotacaoNome}</td>
                  <td>{i.lotacaoEncontradaId ? <span className="text-warning">{i.lotacaoEncontradaUpm} · {i.lotacaoEncontradaNome}</span> : '—'}</td>
                  <td><span className={`badge badge-plain ${corResultado[i.resultado]}`}>{rotulo(i.resultado)}</span></td>
                  <td>{rotulo(i.conservacaoVerificada ?? i.conservacaoAnterior)}</td>
                  <td className="texto-limitado">{i.descricaoAvaria || i.observacao || '—'}</td>
                  <td>{i.fotoAtualId ? <a href={`/api/anexos/${i.fotoAtualId}/download`} target="_blank" rel="noreferrer"><img className="foto-mini" src={`/api/anexos/${i.fotoAtualId}/download`} alt="" /></a> : '—'}</td>
                  <td className="small">{i.verificadoPor}<br />{dataHora(i.verificadoEm)}</td>
                  <td className="text-end text-nowrap">{aberto && i.lotacaoEncontradaId && <button className="btn btn-sm btn-secondary" disabled={ocupado} title="Atualiza a lotação do bem para onde foi encontrado" onClick={() => acao(() => api.levantamentos.movimentar(lid, i.id), 'Bem movimentado para o local encontrado.')}><i className="bi bi-arrow-left-right"></i> Mover</button>}</td>
                </tr>))}</tbody>
            </table></div>)}
        </Section>
      </Card>
    </>
  )
}

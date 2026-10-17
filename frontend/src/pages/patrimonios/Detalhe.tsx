import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { TipoAnexo } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, Carregando, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { data, dataHora, moeda, numero, rotulo } from '../../utils/format'
import { badgeSituacao } from './Lista'

function Dado({ l, v }: { l: string; v: React.ReactNode }) { return <div className="col-md-4 col-lg-3"><div className="kpi-label">{l}</div><div className="fw-medium">{v ?? '—'}</div></div> }

export default function PatrimonioDetalhe() {
  const { id } = useParams()
  const pid = Number(id)
  const { temPerfil } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['patrimonio', id], queryFn: () => api.patrimonios.buscar(pid) })
  const mov = useQuery({ queryKey: ['movimentacoes', id], queryFn: () => api.patrimonios.movimentacoes(pid) })
  const anexos = useQuery({ queryKey: ['anexos', id], queryFn: () => api.patrimonios.anexos(pid) })
  const lotacoes = useQuery({ queryKey: ['lotacoes-todas'], queryFn: api.lotacoes.todas })
  const responsaveis = useQuery({ queryKey: ['responsaveis-todos'], queryFn: api.responsaveis.todos })
  const [painel, setPainel] = useState<'' | 'mover' | 'baixa' | 'anexo'>('')
  const [mv, setMv] = useState({ lotacaoId: '', responsavelId: '', observacao: '' })
  const [motivo, setMotivo] = useState('')
  const [anexo, setAnexo] = useState<{ arquivo: File | null; tipo: TipoAnexo }>({ arquivo: null, tipo: 'NOTA_FISCAL' })
  const [ocupado, setOcupado] = useState(false)
  const oper = temPerfil('ADMINISTRADOR', 'FISCAL')
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['patrimonio', id] }); qc.invalidateQueries({ queryKey: ['movimentacoes', id] }); qc.invalidateQueries({ queryKey: ['anexos', id] }); qc.invalidateQueries({ queryKey: ['patrimonios'] }) }
  const executar = async (fn: () => Promise<unknown>, msg: string) => { setOcupado(true); try { await fn(); toast.sucesso(msg); invalidar(); setPainel('') } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha na operação.') } finally { setOcupado(false) } }

  if (q.isLoading) return <Carregando />
  if (q.error || !q.data) return <Erro erro={q.error ?? 'Bem não encontrado'} />
  const p = q.data
  const mover = (e: FormEvent) => { e.preventDefault(); executar(() => api.patrimonios.movimentar(pid, mv.lotacaoId ? Number(mv.lotacaoId) : '', mv.responsavelId ? Number(mv.responsavelId) : '', mv.observacao), 'Bem movimentado.') }
  const baixar = (e: FormEvent) => { e.preventDefault(); if (!confirm('Confirma a baixa deste bem? A ação não pode ser desfeita.')) return; executar(() => api.patrimonios.baixar(pid, motivo), 'Bem baixado.') }
  const anexar = (e: FormEvent) => { e.preventDefault(); if (!anexo.arquivo) return; executar(() => api.patrimonios.anexar(pid, anexo.arquivo!, anexo.tipo), 'Anexo enviado.').then(() => setAnexo({ arquivo: null, tipo: 'NOTA_FISCAL' })) }
  const excluir = async () => { if (!confirm('Excluir definitivamente este bem?')) return; try { await api.patrimonios.excluir(pid); toast.sucesso('Bem excluído.'); qc.invalidateQueries({ queryKey: ['patrimonios'] }); navigate('/patrimonios') } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha ao excluir.') } }

  return (
    <>
      <PageHeader voltar="/patrimonios" titulo={<><span className="chip-tombo me-2">{p.numeroTombo || 'S/T'}</span>{p.descricao}</>} subtitulo={<><Badge cor={badgeSituacao[p.situacao]}>{rotulo(p.situacao)}</Badge> <span className="ms-2">{p.categoria}{p.subcategoria ? ` · ${p.subcategoria}` : ''}</span></>}
        acoes={oper && <>
          <Link className="btn btn-secondary" to={`/patrimonios/${p.id}/editar`}><i className="bi bi-pencil"></i> Editar</Link>
          {p.situacao !== 'BAIXADO' && <button className="btn btn-secondary" onClick={() => setPainel(painel === 'mover' ? '' : 'mover')}><i className="bi bi-arrow-left-right"></i> Movimentar</button>}
          <button className="btn btn-secondary" onClick={() => setPainel(painel === 'anexo' ? '' : 'anexo')}><i className="bi bi-paperclip"></i> Anexar</button>
          {p.situacao !== 'BAIXADO' && <button className="btn btn-outline-danger" onClick={() => setPainel(painel === 'baixa' ? '' : 'baixa')}><i className="bi bi-x-octagon"></i> Baixar</button>}
          {temPerfil('ADMINISTRADOR') && <button className="btn btn-outline-danger btn-icon" title="Excluir" onClick={excluir}><i className="bi bi-trash"></i></button>}
        </>} />

      {painel === 'mover' && <Card className="mb-3"><form onSubmit={mover}><Section icon="bi-arrow-left-right" titulo="Movimentar bem" desc="Informe a nova lotação e/ou responsável." semBorda>
        <div className="row g-3">
          <div className="col-md-5"><label className="form-label">Nova lotação</label><select className="form-select" value={mv.lotacaoId} onChange={e => setMv({ ...mv, lotacaoId: e.target.value })}><option value="">Manter atual</option>{lotacoes.data?.map(l => <option key={l.id} value={l.id}>{l.upm} · {l.nome}</option>)}</select></div>
          <div className="col-md-4"><label className="form-label">Novo responsável</label><select className="form-select" value={mv.responsavelId} onChange={e => setMv({ ...mv, responsavelId: e.target.value })}><option value="">Manter atual</option>{responsaveis.data?.map(r => <option key={r.id} value={r.id}>{r.nomeCompleto}</option>)}</select></div>
          <div className="col-md-3"><label className="form-label">Observação</label><input className="form-control" value={mv.observacao} onChange={e => setMv({ ...mv, observacao: e.target.value })} /></div>
          <div className="col-12 d-flex gap-2 justify-content-end"><button type="button" className="btn btn-secondary" onClick={() => setPainel('')}>Cancelar</button><button className="btn btn-primary" disabled={ocupado || (!mv.lotacaoId && !mv.responsavelId)}>Confirmar movimentação</button></div>
        </div></Section></form></Card>}
      {painel === 'baixa' && <Card className="mb-3"><form onSubmit={baixar}><Section icon="bi-x-octagon" titulo="Baixa do bem" desc="O bem deixa de compor o patrimônio ativo." semBorda>
        <div className="row g-3"><div className="col-md-9"><label className="form-label required">Motivo</label><input className="form-control" required value={motivo} onChange={e => setMotivo(e.target.value)} /></div>
          <div className="col-md-3 d-flex align-items-end gap-2 justify-content-end"><button type="button" className="btn btn-secondary" onClick={() => setPainel('')}>Cancelar</button><button className="btn btn-danger" disabled={ocupado}>Baixar</button></div></div></Section></form></Card>}
      {painel === 'anexo' && <Card className="mb-3"><form onSubmit={anexar}><Section icon="bi-paperclip" titulo="Anexar arquivo" desc="Nota fiscal, foto, manual ou outro documento." semBorda>
        <div className="row g-3"><div className="col-md-3"><label className="form-label">Tipo</label><select className="form-select" value={anexo.tipo} onChange={e => setAnexo({ ...anexo, tipo: e.target.value as TipoAnexo })}>{(['NOTA_FISCAL', 'FOTO', 'MANUAL', 'OUTRO'] as TipoAnexo[]).map(t => <option key={t} value={t}>{rotulo(t)}</option>)}</select></div>
          <div className="col-md-6"><label className="form-label required">Arquivo</label><input type="file" className="form-control" required onChange={e => setAnexo({ ...anexo, arquivo: e.target.files?.[0] ?? null })} /></div>
          <div className="col-md-3 d-flex align-items-end gap-2 justify-content-end"><button type="button" className="btn btn-secondary" onClick={() => setPainel('')}>Cancelar</button><button className="btn btn-primary" disabled={ocupado || !anexo.arquivo}>Enviar</button></div></div></Section></form></Card>}

      <div className="row g-3">
        <div className="col-lg-8">
          <Card>
            <Section icon="bi-geo-alt" titulo="Localização">
              <div className="row g-3"><Dado l="UPM" v={p.lotacaoUpm} /><Dado l="Lotação" v={p.lotacaoNome} /><Dado l="Responsável" v={p.responsavelNome} /><Dado l="Conservação" v={rotulo(p.conservacao)} /></div>
            </Section>
            <Section icon="bi-cash-coin" titulo="Valores e depreciação">
              <div className="row g-3">
                <Dado l="Data da compra" v={data(p.dataCompra)} /><Dado l="Valor de compra" v={moeda(p.valorCompra)} /><Dado l="Nota fiscal" v={p.notaFiscal} /><Dado l="Vida útil (anos)" v={p.vutAnos ? `${p.vutAnos} (VUD ${numero(p.vudAnos)} · VUR ${numero(p.vurAnos)})` : '—'} />
                <Dado l="Depreciação anual" v={moeda(p.depreciacaoAnual)} /><Dado l="Depreciação acumulada" v={moeda(p.depreciacaoAcumulada)} /><Dado l="Valor contábil líquido" v={moeda(p.valorContabilLiquido)} /><Dado l="Valor recuperável" v={moeda(p.valorRecuperavel)} />
                <Dado l="Perda por impairment" v={moeda(p.perdaImpairment)} /><Dado l="Conclusão" v={p.conclusaoImpairment} />
              </div>
            </Section>
            {(p.situacao === 'BAIXADO') && <Section icon="bi-x-octagon" titulo="Baixa"><div className="row g-3"><Dado l="Data" v={data(p.dataBaixa)} /><div className="col-md-8"><div className="kpi-label">Motivo</div><div>{p.motivoBaixa}</div></div></div></Section>}
            <Section icon="bi-card-text" titulo="Complementos" semBorda>
              <div className="row g-3">
                <div className="col-12"><div className="kpi-label">Observação</div><div className="obs-pre">{p.observacao || '—'}</div></div>
                {p.linkReferencia && <div className="col-12"><div className="kpi-label">Referência</div><a href={p.linkReferencia} target="_blank" rel="noreferrer">{p.linkReferencia}</a></div>}
                <div className="col-12 small text-muted">Cadastrado por {p.criadoPor ?? '—'} em {dataHora(p.criadoEm)} · Atualizado por {p.atualizadoPor ?? '—'} em {dataHora(p.atualizadoEm)}</div>
              </div>
            </Section>
          </Card>
        </div>
        <div className="col-lg-4">
          <Card className="mb-3">
            <Section icon="bi-paperclip" titulo="Anexos" semBorda>
              {!anexos.data?.length ? <div className="text-muted small">Nenhum anexo.</div> : <ul className="list-unstyled mb-0 d-grid gap-2">{anexos.data.map(a => (
                <li key={a.id} className="d-flex align-items-center gap-2">
                  <i className={`bi ${a.tipo === 'FOTO' ? 'bi-image' : 'bi-file-earmark-text'} text-muted`}></i>
                  <a className="flex-grow-1 text-truncate" href={`/api/anexos/${a.id}/download`} target="_blank" rel="noreferrer" title={a.nomeOriginal}>{a.nomeOriginal}</a>
                  <span className="badge badge-plain text-bg-light">{rotulo(a.tipo)}</span>
                  {oper && <button className="btn btn-sm btn-icon btn-outline-danger" title="Remover" onClick={() => { if (confirm('Remover anexo?')) executar(() => api.patrimonios.excluirAnexo(a.id), 'Anexo removido.') }}><i className="bi bi-trash"></i></button>}
                </li>))}</ul>}
            </Section>
          </Card>
          <Card>
            <Section icon="bi-clock-history" titulo="Histórico de movimentações" semBorda>
              {!mov.data?.length ? <div className="text-muted small">Nenhuma movimentação.</div> : <div className="d-grid gap-3">{mov.data.map(m => (
                <div key={m.id} className="border-start ps-3" style={{ borderColor: 'var(--primary-300) !important' }}>
                  <div className="small text-muted">{dataHora(m.dataMovimentacao)} · {m.executadoPor}</div>
                  <div className="small"><strong>{m.lotacaoOrigemUpm} · {m.lotacaoOrigemNome}</strong> → <strong>{m.lotacaoDestinoUpm} · {m.lotacaoDestinoNome}</strong></div>
                  {(m.responsavelOrigemNome || m.responsavelDestinoNome) && <div className="small text-muted">{m.responsavelOrigemNome ?? '—'} → {m.responsavelDestinoNome ?? '—'}</div>}
                  {m.observacao && <div className="small fst-italic">{m.observacao}</div>}
                </div>))}</div>}
            </Section>
          </Card>
        </div>
      </div>
    </>
  )
}

import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api'
import { useAuth } from '../../auth/AuthContext'
import { Badge, Card, Carregando, EmptyState, Erro, Filtro, PageHeader, Paginacao } from '../../components/ui'
import { data, moeda, rotulo, CONSERVACOES, SITUACOES } from '../../utils/format'

export const badgeSituacao: Record<string, string> = { ATIVO: 'text-bg-success', BAIXADO: 'text-bg-secondary', MANUTENCAO: 'text-bg-warning', CAUTELADO: 'text-bg-info', EM_APURACAO: 'text-bg-danger' }

export default function PatrimoniosLista() {
  const { temPerfil } = useAuth()
  const [params, setParams] = useSearchParams()
  const filtroAtual = Object.fromEntries(params.entries())
  const [f, setF] = useState<Record<string, string>>({ descricao: '', numeroTombo: '', upm: '', categoria: '', situacao: '', conservacao: '', lotacaoId: '', ...filtroAtual })
  const page = Number(params.get('page') || 0)
  const q = useQuery({ queryKey: ['patrimonios', filtroAtual], queryFn: () => api.patrimonios.pesquisar(filtroAtual, page) })
  const upms = useQuery({ queryKey: ['upms'], queryFn: api.catalogos.upms })
  const categorias = useQuery({ queryKey: ['categorias'], queryFn: api.catalogos.categorias })
  const lotacoes = useQuery({ queryKey: ['lotacoes-todas'], queryFn: api.lotacoes.todas })

  const aplicar = (extra: Record<string, string> = {}) => {
    const n: Record<string, string> = {}
    Object.entries({ ...f, ...extra }).forEach(([k, v]) => { if (v) n[k] = v })
    setParams(n)
  }
  const oper = temPerfil('ADMINISTRADOR', 'FISCAL')
  return (
    <>
      <PageHeader titulo="Patrimônios" subtitulo="Bens cadastrados na Fundação." acoes={oper && <Link className="btn btn-primary" to="/patrimonios/novo"><i className="bi bi-plus-lg"></i> Novo bem</Link>} />
      <Filtro onSubmit={() => aplicar({ page: '' })} onLimpar={() => { setF({ descricao: '', numeroTombo: '', upm: '', categoria: '', situacao: '', conservacao: '', lotacaoId: '' }); setParams({}) }}>
        <div className="col-md-3"><label className="form-label small">Descrição</label><input className="form-control form-control-sm" value={f.descricao} onChange={e => setF({ ...f, descricao: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Tombo</label><input className="form-control form-control-sm" value={f.numeroTombo} onChange={e => setF({ ...f, numeroTombo: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">UPM</label><select className="form-select form-select-sm" value={f.upm} onChange={e => setF({ ...f, upm: e.target.value })}><option value="">Todas</option>{upms.data?.map(u => <option key={u}>{u}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">Lotação</label><select className="form-select form-select-sm" value={f.lotacaoId} onChange={e => setF({ ...f, lotacaoId: e.target.value })}><option value="">Todas</option>{lotacoes.data?.map(l => <option key={l.id} value={l.id}>{l.upm} · {l.nome}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">Categoria</label><select className="form-select form-select-sm" value={f.categoria} onChange={e => setF({ ...f, categoria: e.target.value })}><option value="">Todas</option>{categorias.data?.map(c => <option key={c}>{c}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">Situação</label><select className="form-select form-select-sm" value={f.situacao} onChange={e => setF({ ...f, situacao: e.target.value })}><option value="">Todas</option>{SITUACOES.map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">Conservação</label><select className="form-select form-select-sm" value={f.conservacao} onChange={e => setF({ ...f, conservacao: e.target.value })}><option value="">Todas</option>{CONSERVACOES.map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></div>
      </Filtro>
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-box-seam" titulo="Nenhum bem encontrado" desc="Ajuste os filtros ou cadastre um novo bem." ctaTo={oper ? '/patrimonios/novo' : undefined} ctaLabel="Novo bem" /> : (
          <>
            <div className="table-responsive table-livre">
              <table className="table table-hover table-compacta mb-0">
                <thead><tr><th>Tombo</th><th>Descrição</th><th>Lotação</th><th>Responsável</th><th>Situação</th><th>Conservação</th><th className="text-end">Valor</th><th></th></tr></thead>
                <tbody>
                  {q.data!.content.map(p => (
                    <tr key={p.id}>
                      <td><span className="chip-tombo">{p.numeroTombo || 'S/T'}</span></td>
                      <td><div className="cell-stack"><Link to={`/patrimonios/${p.id}`} className="cell-primary text-decoration-none">{p.descricao}</Link><span className="cell-secondary">{p.categoria}{p.subcategoria ? ` · ${p.subcategoria}` : ''}</span></div></td>
                      <td><div className="cell-stack"><span className="cell-primary">{p.lotacaoNome}</span><span className="cell-secondary">{p.lotacaoUpm}</span></div></td>
                      <td>{p.responsavelNome || '—'}</td>
                      <td><Badge cor={badgeSituacao[p.situacao] ?? 'text-bg-secondary'}>{rotulo(p.situacao)}</Badge></td>
                      <td>{rotulo(p.conservacao)}</td>
                      <td className="text-end resumo-valor">{moeda(p.valorCompra)}<div className="cell-secondary small text-muted">{data(p.dataCompra)}</div></td>
                      <td className="text-end text-nowrap">
                        <Link className="btn btn-sm btn-secondary btn-icon" to={`/patrimonios/${p.id}`} title="Detalhes"><i className="bi bi-eye"></i></Link>
                        {oper && <Link className="btn btn-sm btn-secondary btn-icon ms-1" to={`/patrimonios/${p.id}/editar`} title="Editar"><i className="bi bi-pencil"></i></Link>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="px-3 pb-3"><div className="small text-muted mt-2">{q.data!.totalElements} registro(s)</div><Paginacao pagina={q.data!} onPage={n => aplicar({ page: String(n) })} /></div>
          </>
        )}
      </Card>
    </>
  )
}

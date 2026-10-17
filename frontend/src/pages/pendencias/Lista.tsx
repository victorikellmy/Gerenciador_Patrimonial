import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api'
import type { StatusPendencia } from '../../api/types'
import { Card, Carregando, EmptyState, Erro, PageHeader, Paginacao } from '../../components/ui'
import { dataHora, moeda, rotulo } from '../../utils/format'

const cor: Record<string, string> = { PENDENTE: 'text-bg-warning', CONCLUIDA: 'text-bg-success', DESCARTADA: 'text-bg-secondary' }

export default function PendenciasLista() {
  const [status, setStatus] = useState<StatusPendencia | ''>('PENDENTE')
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['pendencias', status, page], queryFn: () => api.pendencias.listar(status, page) })
  const cont = useQuery({ queryKey: ['pendencias-contagem'], queryFn: api.pendencias.contagem })
  return (
    <>
      <PageHeader titulo="Pendências de patrimoniamento" subtitulo="Bens recebidos do Almoxarifado aguardando registro patrimonial." />
      <div className="d-flex gap-2 mb-3 flex-wrap">
        {(['PENDENTE', 'CONCLUIDA', 'DESCARTADA', ''] as const).map(s => <button key={s} className={`btn btn-sm ${status === s ? 'btn-primary' : 'btn-secondary'}`} onClick={() => { setStatus(s); setPage(0) }}>{s ? rotulo(s) : 'Todas'}{s && cont.data?.[s] !== undefined ? ` (${cont.data[s]})` : ''}</button>)}
      </div>
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-inbox" titulo="Nenhuma pendência" desc="Quando o Almoxarifado registrar a entrega de um bem patrimonial, ele aparecerá aqui." /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>#</th><th>Recebido em</th><th>Documento</th><th>Fornecedor / NF</th><th>Itens</th><th>Destino</th><th className="text-end">Valor</th><th>Progresso</th><th>Status</th><th></th></tr></thead>
              <tbody>{q.data!.content.map(p => (
                <tr key={p.id}>
                  <td>{p.id}</td><td className="text-nowrap">{dataHora(p.dataRecebimento)}</td>
                  <td><div className="cell-stack"><span className="cell-primary">{p.numeroDocumento || `Compra ${p.compraIdOrigem}`}</span><span className="cell-secondary">{p.assunto}</span></div></td>
                  <td><div className="cell-stack"><span className="cell-primary">{p.fornecedor || '—'}</span><span className="cell-secondary">NF {p.numeroNotaFiscal || '—'}</span></div></td>
                  <td className="texto-limitado">{p.itens.map(i => `${i.quantidade ?? 1}× ${i.descricao}`).join('; ')}</td>
                  <td>{p.setorDestino || '—'}</td><td className="text-end">{moeda(p.valorTotal)}</td>
                  <td>{p.quantidadePatrimoniada}/{p.quantidadeTotal}</td>
                  <td><span className={`badge badge-plain ${cor[p.status]}`}>{rotulo(p.status)}</span></td>
                  <td className="text-end"><Link className="btn btn-sm btn-primary" to={`/pendencias/${p.id}`}>{p.status === 'PENDENTE' ? 'Patrimoniar' : 'Ver'}</Link></td>
                </tr>))}</tbody>
            </table></div>
            <div className="px-3 pb-3"><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

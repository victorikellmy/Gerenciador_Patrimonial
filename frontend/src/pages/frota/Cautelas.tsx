import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, Filtro, PageHeader, Paginacao, useToast } from '../../components/ui'
import { agoraHora, data, hora, numero } from '../../utils/format'

export default function Cautelas() {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState({ veiculoId: '', de: '', ate: '', texto: '', somenteAbertos: false })
  const [aplicado, setAplicado] = useState(f)
  const [page, setPage] = useState(0)
  const [fin, setFin] = useState<{ id: number; kmFinal: string; hora: string } | null>(null)
  const veiculos = useQuery({ queryKey: ['veiculos', true], queryFn: () => api.frota.veiculos(true) })
  const q = useQuery({ queryKey: ['cautelas', aplicado, page], queryFn: () => api.frota.cautelas(aplicado, page) })
  const invalidar = () => { qc.invalidateQueries({ queryKey: ['cautelas'] }); qc.invalidateQueries({ queryKey: ['veiculos'] }) }
  const finalizar = async () => { if (!fin) return; try { await api.frota.finalizarCautela(fin.id, Number(fin.kmFinal), fin.hora); toast.sucesso('Cautela finalizada.'); setFin(null); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  const excluir = async (id: number) => { if (!confirm('Excluir esta cautela?')) return; try { await api.frota.excluirCautela(id); toast.sucesso('Excluída.'); invalidar() } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  return (
    <>
      <PageHeader titulo="Cautelas de viatura" subtitulo="Entrega de veículo a condutor externo com termo de responsabilidade." acoes={temPerfil('ADMINISTRADOR', 'FISCAL', 'MOTORISTA') && <Link className="btn btn-primary" to="/frota/cautelas/nova"><i className="bi bi-plus-lg"></i> Nova cautela</Link>} />
      <Filtro onSubmit={() => { setAplicado(f); setPage(0) }} onLimpar={() => { const v = { veiculoId: '', de: '', ate: '', texto: '', somenteAbertos: false }; setF(v); setAplicado(v) }}>
        <div className="col-md-3"><label className="form-label small">Veículo</label><select className="form-select form-select-sm" value={f.veiculoId} onChange={e => setF({ ...f, veiculoId: e.target.value })}><option value="">Todos</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">De</label><input type="date" className="form-control form-control-sm" value={f.de} onChange={e => setF({ ...f, de: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Até</label><input type="date" className="form-control form-control-sm" value={f.ate} onChange={e => setF({ ...f, ate: e.target.value })} /></div>
        <div className="col-md-3"><label className="form-label small">Texto</label><input className="form-control form-control-sm" placeholder="condutor, destino..." value={f.texto} onChange={e => setF({ ...f, texto: e.target.value })} /></div>
        <div className="col-md-1"><div className="form-check mt-4"><input className="form-check-input" type="checkbox" id="abertas" checked={f.somenteAbertos} onChange={e => setF({ ...f, somenteAbertos: e.target.checked })} /><label className="form-check-label small" htmlFor="abertas">Abertas</label></div></div>
      </Filtro>
      {fin && <Card className="mb-3"><div className="p-3 row g-2 align-items-end"><div className="col-md-3"><label className="form-label">Km de chegada</label><input type="number" className="form-control" value={fin.kmFinal} onChange={e => setFin({ ...fin, kmFinal: e.target.value })} /></div><div className="col-md-3"><label className="form-label">Hora</label><input type="time" className="form-control" value={fin.hora} onChange={e => setFin({ ...fin, hora: e.target.value })} /></div><div className="col-md-6 d-flex gap-2"><button className="btn btn-primary" disabled={!fin.kmFinal} onClick={finalizar}>Registrar devolução</button><button className="btn btn-secondary" onClick={() => setFin(null)}>Cancelar</button></div></div></Card>}
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-key" titulo="Nenhuma cautela" /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              <thead><tr><th>Data</th><th>Veículo</th><th>Condutor</th><th>Lotação</th><th>Destino</th><th>Motivo</th><th>Saída</th><th>Devolução</th><th className="text-end">Km</th><th></th></tr></thead>
              <tbody>{q.data!.content.map(c => <tr key={c.id} className={c.emAndamento ? 'row-na-rua' : ''}>
                <td className="text-nowrap">{data(c.dataViagem)}</td><td><Link to={`/frota/${c.veiculoId}`}>{c.placa}</Link> <span className="text-muted small">{c.modelo}</span></td>
                <td><div className="cell-stack"><span className="cell-primary">{c.motoristaNome}</span><span className="cell-secondary">CPF {c.cpf} · {c.fone}</span></div></td>
                <td>{c.lotacao}</td><td className="texto-limitado">{c.destino}</td><td>{c.motivo}</td>
                <td className="text-nowrap">{hora(c.horaSaida)} · {numero(c.kmInicial)} km</td>
                <td className="text-nowrap">{c.emAndamento ? <span className="badge badge-plain text-bg-info">em aberto</span> : `${hora(c.horaChegada)} · ${numero(c.kmFinal)} km`}</td>
                <td className="text-end">{numero(c.kmRodados)}</td>
                <td className="text-end text-nowrap">
                  <button className="btn btn-sm btn-secondary btn-icon" title="Termo de cautela (PDF)" onClick={() => abrirArquivo(api.frota.termoUrl(c.id))}><i className="bi bi-file-earmark-pdf"></i></button>{' '}
                  {c.emAndamento && temPerfil('ADMINISTRADOR', 'FISCAL', 'MOTORISTA') && <button className="btn btn-sm btn-primary btn-icon" title="Registrar devolução" onClick={() => setFin({ id: c.id, kmFinal: '', hora: agoraHora() })}><i className="bi bi-box-arrow-in-left"></i></button>}{' '}
                  {temPerfil('ADMINISTRADOR', 'FISCAL') && <Link className="btn btn-sm btn-secondary btn-icon" title="Editar" to={`/frota/cautelas/${c.id}/editar`}><i className="bi bi-pencil"></i></Link>}{' '}
                  {temPerfil('ADMINISTRADOR') && <button className="btn btn-sm btn-outline-danger btn-icon" title="Excluir" onClick={() => excluir(c.id)}><i className="bi bi-trash"></i></button>}
                </td></tr>)}</tbody>
            </table></div>
            <div className="px-3 pb-3"><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

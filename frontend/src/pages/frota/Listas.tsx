import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import type { Abastecimento, Deslocamento, Manutencao, Page } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, EmptyState, Erro, Filtro, PageHeader, Paginacao, useToast } from '../../components/ui'
import { data, hora, moeda, numero, rotulo } from '../../utils/format'

type Tipo = 'abastecimentos' | 'manutencoes' | 'deslocamentos'
const titulos: Record<Tipo, [string, string]> = { abastecimentos: ['Abastecimentos', 'Todos os abastecimentos da frota.'], manutencoes: ['Manutenções', 'Serviços, revisões e consertos.'], deslocamentos: ['Deslocamentos', 'Saídas e chegadas das viaturas.'] }

export default function FrotaListas({ tipo }: { tipo: Tipo }) {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [f, setF] = useState({ veiculoId: '', de: '', ate: '', texto: '', somenteAbertos: false })
  const [aplicado, setAplicado] = useState(f)
  const [page, setPage] = useState(0)
  const veiculos = useQuery({ queryKey: ['veiculos', true], queryFn: () => api.frota.veiculos(true) })
  const q = useQuery({ queryKey: ['frota-lista', tipo, aplicado, page], queryFn: (): Promise<Page<Abastecimento | Manutencao | Deslocamento>> => tipo === 'abastecimentos' ? api.frota.abastecimentos(aplicado, page) : tipo === 'manutencoes' ? api.frota.manutencoes(aplicado, page) : api.frota.deslocamentos(aplicado, page) })
  const admin = temPerfil('ADMINISTRADOR')
  const gestor = temPerfil('ADMINISTRADOR', 'FISCAL')
  const excluir = async (id: number) => {
    if (!confirm('Excluir este lançamento?')) return
    try { await (tipo === 'abastecimentos' ? api.frota.excluirAbastecimento(id) : tipo === 'manutencoes' ? api.frota.excluirManutencao(id) : api.frota.excluirDeslocamento(id)); toast.sucesso('Excluído.'); qc.invalidateQueries({ queryKey: ['frota-lista'] }) } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') }
  }
  const [titulo, sub] = titulos[tipo]
  return (
    <>
      <PageHeader titulo={titulo} subtitulo={sub} acoes={<>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.frota.exportUrl(tipo, aplicado, 'xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button>
        <button className="btn btn-secondary" onClick={() => abrirArquivo(api.frota.exportUrl(tipo, aplicado, 'pdf'))}><i className="bi bi-file-earmark-pdf"></i> PDF</button>
      </>} />
      <Filtro onSubmit={() => { setAplicado(f); setPage(0) }} onLimpar={() => { const v = { veiculoId: '', de: '', ate: '', texto: '', somenteAbertos: false }; setF(v); setAplicado(v); setPage(0) }}>
        <div className="col-md-3"><label className="form-label small">Veículo</label><select className="form-select form-select-sm" value={f.veiculoId} onChange={e => setF({ ...f, veiculoId: e.target.value })}><option value="">Todos</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}</option>)}</select></div>
        <div className="col-md-2"><label className="form-label small">De</label><input type="date" className="form-control form-control-sm" value={f.de} onChange={e => setF({ ...f, de: e.target.value })} /></div>
        <div className="col-md-2"><label className="form-label small">Até</label><input type="date" className="form-control form-control-sm" value={f.ate} onChange={e => setF({ ...f, ate: e.target.value })} /></div>
        <div className="col-md-3"><label className="form-label small">Texto</label><input className="form-control form-control-sm" placeholder="motorista, destino, posto..." value={f.texto} onChange={e => setF({ ...f, texto: e.target.value })} /></div>
        {tipo === 'deslocamentos' && <div className="col-md-1"><div className="form-check mt-4"><input className="form-check-input" type="checkbox" id="abertos" checked={f.somenteAbertos} onChange={e => setF({ ...f, somenteAbertos: e.target.checked })} /><label className="form-check-label small" htmlFor="abertos">Na rua</label></div></div>}
      </Filtro>
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : q.data!.empty ? <EmptyState icon="bi-clock-history" titulo="Nenhum lançamento" /> : (
          <>
            <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
              {tipo === 'abastecimentos' && <><thead><tr><th>Data</th><th>Veículo</th><th>Combustível</th><th className="text-end">Litros</th><th className="text-end">Valor</th><th className="text-end">R$/L</th><th className="text-end">Km</th><th>Posto</th><th>Motorista</th><th>NF</th><th></th></tr></thead>
                <tbody>{(q.data!.content as Abastecimento[]).map(a => <tr key={a.id}><td className="text-nowrap">{data(a.data)}</td><td><Link to={`/frota/${a.veiculoId}`}>{a.placa}</Link> <span className="text-muted small">{a.modelo}</span></td><td>{rotulo(a.combustivel)}</td><td className="text-end">{numero(a.quantidadeLitros, 2)}</td><td className="text-end">{moeda(a.valor)}</td><td className="text-end">{a.precoLitro ? numero(a.precoLitro, 3) : '—'}</td><td className="text-end">{numero(a.km)}</td><td>{a.postoCidade || '—'}</td><td>{a.motorista}</td><td>{a.possuiNotaFiscal ? <button className="btn btn-sm btn-link p-0" onClick={() => abrirArquivo(api.frota.nfUrl('abastecimentos', a.id))}><i className="bi bi-paperclip"></i></button> : <span className="text-danger small">sem NF</span>}</td><td className="text-end text-nowrap">{gestor && <Link className="btn btn-sm btn-secondary btn-icon" to={`/frota/abastecimentos/${a.id}/editar`}><i className="bi bi-pencil"></i></Link>}{admin && <button className="btn btn-sm btn-outline-danger btn-icon ms-1" onClick={() => excluir(a.id)}><i className="bi bi-trash"></i></button>}</td></tr>)}</tbody></>}
              {tipo === 'manutencoes' && <><thead><tr><th>Data</th><th>Veículo</th><th>Serviço</th><th>Descrição</th><th className="text-end">Valor</th><th className="text-end">Km</th><th>Fornecedor</th><th>Próx. revisão</th><th>Registro</th><th>NF</th><th></th></tr></thead>
                <tbody>{(q.data!.content as Manutencao[]).map(m => <tr key={m.id}><td className="text-nowrap">{data(m.data)}</td><td><Link to={`/frota/${m.veiculoId}`}>{m.placa}</Link> <span className="text-muted small">{m.modelo}</span></td><td>{rotulo(m.servico)}</td><td className="texto-limitado">{m.descricao || '—'}</td><td className="text-end">{moeda(m.valor)}</td><td className="text-end">{numero(m.km)}</td><td>{m.fornecedor || '—'}</td><td>{m.proximaRevisaoKm ? `${numero(m.proximaRevisaoKm)} km` : ''}{m.proximaRevisaoData ? ` ${data(m.proximaRevisaoData)}` : ''}</td><td>{m.motorista}</td><td>{m.possuiNotaFiscal ? <button className="btn btn-sm btn-link p-0" onClick={() => abrirArquivo(api.frota.nfUrl('manutencoes', m.id))}><i className="bi bi-paperclip"></i></button> : '—'}</td><td className="text-end text-nowrap">{gestor && <Link className="btn btn-sm btn-secondary btn-icon" to={`/frota/manutencoes/${m.id}/editar`}><i className="bi bi-pencil"></i></Link>}{admin && <button className="btn btn-sm btn-outline-danger btn-icon ms-1" onClick={() => excluir(m.id)}><i className="bi bi-trash"></i></button>}</td></tr>)}</tbody></>}
              {tipo === 'deslocamentos' && <><thead><tr><th>Data</th><th>Veículo</th><th>Motorista</th><th>Destino</th><th>Motivo</th><th>Titular</th><th>Saída</th><th>Chegada</th><th className="text-end">Km rodados</th><th></th></tr></thead>
                <tbody>{(q.data!.content as Deslocamento[]).map(d => <tr key={d.id} className={d.emAndamento ? 'row-na-rua' : ''}><td className="text-nowrap">{data(d.dataViagem)}</td><td><Link to={`/frota/${d.veiculoId}`}>{d.placa}</Link> <span className="text-muted small">{d.modelo}</span></td><td>{d.motorista}</td><td className="texto-limitado">{d.destino}</td><td>{d.motivo}</td><td>{d.titular || '—'}</td><td className="text-nowrap">{hora(d.horaSaida)} · {numero(d.kmInicial)} km</td><td className="text-nowrap">{d.emAndamento ? <span className="badge badge-plain text-bg-info">na rua</span> : `${hora(d.horaChegada)} · ${numero(d.kmFinal)} km`}</td><td className="text-end">{numero(d.kmRodados)}</td><td className="text-end text-nowrap">{gestor && <Link className="btn btn-sm btn-secondary btn-icon" to={`/frota/deslocamentos/${d.id}/editar`}><i className="bi bi-pencil"></i></Link>}{admin && <button className="btn btn-sm btn-outline-danger btn-icon ms-1" onClick={() => excluir(d.id)}><i className="bi bi-trash"></i></button>}</td></tr>)}</tbody></>}
            </table></div>
            <div className="px-3 pb-3"><div className="small text-muted mt-2">{q.data!.totalElements} registro(s)</div><Paginacao pagina={q.data!} onPage={setPage} /></div>
          </>)}
      </Card>
    </>
  )
}

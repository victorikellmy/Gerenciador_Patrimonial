import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { NivelDiaria } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Card, Carregando, Erro, PageHeader, Section, useToast } from '../../components/ui'
import { moeda } from '../../utils/format'

const vazio = { descricao: '', valorCapitais: '' as number | '', valorInteriorEstado: '' as number | '', valorInteriorOutroEstado: '' as number | '' }

export default function Niveis() {
  const { temPerfil } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const q = useQuery({ queryKey: ['niveis', 'todos'], queryFn: () => api.diarias.niveis(false) })
  const [form, setForm] = useState<typeof vazio & { id?: number } | null>(null)
  const [salvando, setSalvando] = useState(false)
  const admin = temPerfil('ADMINISTRADOR')
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); if (!form) return; setSalvando(true)
    try { await api.diarias.salvarNivel(form.id ?? null, { descricao: form.descricao, valorCapitais: Number(form.valorCapitais), valorInteriorEstado: Number(form.valorInteriorEstado), valorInteriorOutroEstado: Number(form.valorInteriorOutroEstado) }); toast.sucesso('Tabela atualizada.'); setForm(null); qc.invalidateQueries({ queryKey: ['niveis'] }); qc.invalidateQueries({ queryKey: ['profissionais'] }) }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } finally { setSalvando(false) }
  }
  const alternar = async (n: NivelDiaria) => { try { await api.diarias.ativoNivel(n.id, !n.ativo); qc.invalidateQueries({ queryKey: ['niveis'] }) } catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha.') } }
  return (
    <>
      <PageHeader titulo="Tabela de valores de diárias" subtitulo="Valor por cargo/posto/graduação conforme o destino." acoes={admin && <button className="btn btn-primary" onClick={() => setForm({ ...vazio })}><i className="bi bi-plus-lg"></i> Novo nível</button>} />
      {form && <Card className="mb-3"><form onSubmit={salvar}><Section icon="bi-table" titulo={form.id ? 'Editar nível' : 'Novo nível'} semBorda>
        <div className="row g-3">
          <div className="col-md-5"><label className="form-label required">Descrição</label><input className="form-control" required value={form.descricao} onChange={e => setForm({ ...form, descricao: e.target.value })} /></div>
          <div className="col-md-2"><label className="form-label required">Capitais (R$)</label><input type="number" step="0.01" min="0" className="form-control" required value={form.valorCapitais} onChange={e => setForm({ ...form, valorCapitais: e.target.value === '' ? '' : Number(e.target.value) })} /></div>
          <div className="col-md-2"><label className="form-label required">Interior TO (R$)</label><input type="number" step="0.01" min="0" className="form-control" required value={form.valorInteriorEstado} onChange={e => setForm({ ...form, valorInteriorEstado: e.target.value === '' ? '' : Number(e.target.value) })} /></div>
          <div className="col-md-2"><label className="form-label required">Outro estado (R$)</label><input type="number" step="0.01" min="0" className="form-control" required value={form.valorInteriorOutroEstado} onChange={e => setForm({ ...form, valorInteriorOutroEstado: e.target.value === '' ? '' : Number(e.target.value) })} /></div>
          <div className="col-md-1 d-flex align-items-end gap-1"><button type="button" className="btn btn-secondary btn-icon" onClick={() => setForm(null)}><i className="bi bi-x-lg"></i></button><button className="btn btn-primary btn-icon" disabled={salvando}><i className="bi bi-check-lg"></i></button></div>
        </div></Section></form></Card>}
      <Card>
        {q.isLoading ? <Carregando /> : q.error ? <Erro erro={q.error} /> : (
          <div className="table-responsive table-livre"><table className="table table-hover table-compacta mb-0">
            <thead><tr><th>Cargo / posto / graduação</th><th className="text-end">Capitais</th><th className="text-end">Interior do Estado</th><th className="text-end">Interior de outro Estado</th><th>Situação</th><th></th></tr></thead>
            <tbody>{q.data?.map(n => <tr key={n.id} className={n.ativo ? '' : 'text-muted'}><td><strong>{n.descricao}</strong></td><td className="text-end">{moeda(n.valorCapitais)}</td><td className="text-end">{moeda(n.valorInteriorEstado)}</td><td className="text-end">{moeda(n.valorInteriorOutroEstado)}</td><td><span className={`badge badge-plain ${n.ativo ? 'text-bg-success' : 'text-bg-secondary'}`}>{n.ativo ? 'Ativo' : 'Inativo'}</span></td>
              <td className="text-end text-nowrap">{admin && <><button className="btn btn-sm btn-secondary btn-icon" onClick={() => setForm({ id: n.id, descricao: n.descricao, valorCapitais: n.valorCapitais, valorInteriorEstado: n.valorInteriorEstado, valorInteriorOutroEstado: n.valorInteriorOutroEstado })}><i className="bi bi-pencil"></i></button>{' '}<button className={`btn btn-sm btn-icon ${n.ativo ? 'btn-outline-danger' : 'btn-outline-success'}`} onClick={() => alternar(n)}><i className={`bi ${n.ativo ? 'bi-x-circle' : 'bi-check-circle'}`}></i></button></>}</td></tr>)}</tbody>
          </table></div>)}
      </Card>
    </>
  )
}

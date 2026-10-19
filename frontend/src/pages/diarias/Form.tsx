import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { DiariaRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { moeda } from '../../utils/format'

const vazio: DiariaRequest = { profissionalId: '', itinerario: '', partida: '', horaSaida: '', retorno: '', horaChegada: '', meioTransporte: 'VEICULO_OFICIAL', recurso: 'PROPRIO', numeroDiarias: '', valorUnitario: '', finalidade: '' }

export default function DiariaForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['diaria', id], queryFn: () => api.diarias.buscar(Number(id)), enabled: editando })
  const profissionais = useQuery({ queryKey: ['profissionais', 'ativos'], queryFn: () => api.diarias.profissionais(undefined, true) })
  const inicial = useMemo<DiariaRequest>(() => { const d = existente.data; if (!d) return vazio; return { profissionalId: d.profissionalId, itinerario: d.itinerario, partida: d.partida, horaSaida: d.horaSaida?.substring(0, 5) ?? '', retorno: d.retorno, horaChegada: d.horaChegada?.substring(0, 5) ?? '', meioTransporte: d.meioTransporte, recurso: d.recurso, numeroDiarias: d.numeroDiarias, valorUnitario: d.valorUnitario, finalidade: d.finalidade ?? '' } }, [existente.data])
  const { valores, bind, bindNum, set } = useForm(inicial)
  const prof = profissionais.data?.find(p => p.id === Number(valores.profissionalId))
  const total = (Number(valores.numeroDiarias) || 0) * (Number(valores.valorUnitario) || 0)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try { const d = editando ? await api.diarias.atualizar(Number(id), valores) : await api.diarias.criar(valores); qc.invalidateQueries({ queryKey: ['diarias'] }); qc.invalidateQueries({ queryKey: ['diarias-contagens'] }); qc.invalidateQueries({ queryKey: ['diaria', String(d.id)] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }); toast.sucesso(editando ? 'Solicitação atualizada.' : 'Solicitação aberta.'); navigate(`/diarias/${d.id}`) }
    catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  const voltar = editando ? `/diarias/${id}` : '/diarias'
  return (
    <>
      <PageHeader voltar={voltar} titulo={editando ? 'Editar solicitação de diária' : 'Nova solicitação de diária'} subtitulo="Preencha os dados da viagem. O valor unitário vem da tabela do cargo do profissional." />
      {editando && existente.data && existente.data.status !== 'SOLICITADO' && (
        <div className="alert alert-warning"><i className="bi bi-exclamation-triangle-fill"></i> Esta diária está <strong>{existente.data.status === 'CONFIRMADO' ? 'confirmada' : existente.data.status === 'DEFERIDO' ? 'deferida' : 'indeferida'}</strong>. Ao salvar alterações ela volta para <strong>Solicitada</strong> e precisa de nova confirmação e novo deferimento. Os valores de antes e depois ficam no histórico.</div>
      )}
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-person-vcard" titulo="Profissional">
          <div className="row g-3">
            <Campo label="Profissional" obrigatorio col="col-md-6"><select className="form-select" required {...bindNum('profissionalId')}><option value="">Selecione</option>{profissionais.data?.map(p => <option key={p.id} value={p.id}>{p.nome}{p.matricula ? ` (${p.matricula})` : ''} · {p.nivelDescricao}</option>)}</select></Campo>
            <div className="col-md-6"><label className="form-label">Valores da tabela</label><div className="d-flex flex-wrap gap-2">
              {!prof ? <span className="text-muted small">Selecione o profissional para ver os valores.</span> : ([['Capitais', prof.valorCapitais], ['Interior do Estado', prof.valorInteriorEstado], ['Interior de outro Estado', prof.valorInteriorOutroEstado]] as const).map(([r, v]) => <button key={r} type="button" className={`btn btn-sm ${Number(valores.valorUnitario) === Number(v) ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => set('valorUnitario', Number(v))}>{r}: {moeda(v)}</button>)}
            </div></div>
          </div>
        </Section>
        <Section icon="bi-geo-alt" titulo="Viagem">
          <div className="row g-3">
            <Campo label="Itinerário" obrigatorio col="col-md-8"><input className="form-control" required maxLength={200} placeholder="Ex: Palmas → Araguaína → Palmas" {...bind('itinerario')} /></Campo>
            <Campo label="Meio de transporte" obrigatorio col="col-md-2"><select className="form-select" {...bind('meioTransporte')}><option value="VEICULO_OFICIAL">Veículo oficial</option><option value="AEREO">Aéreo</option><option value="RODOVIARIO">Rodoviário</option></select></Campo>
            <Campo label="Recurso" obrigatorio col="col-md-2"><select className="form-select" {...bind('recurso')}><option value="PROPRIO">Próprio</option><option value="CONVENIO">Convênio</option></select></Campo>
            <Campo label="Partida" obrigatorio col="col-md-3"><input type="date" className="form-control" required {...bind('partida')} /></Campo>
            <Campo label="Hora de saída" col="col-md-3"><input type="time" className="form-control" {...bind('horaSaida')} /></Campo>
            <Campo label="Retorno" obrigatorio col="col-md-3"><input type="date" className="form-control" required min={valores.partida || undefined} {...bind('retorno')} /></Campo>
            <Campo label="Hora de chegada" col="col-md-3"><input type="time" className="form-control" {...bind('horaChegada')} /></Campo>
          </div>
        </Section>
        <Section icon="bi-cash-coin" titulo="Valores" semBorda>
          <div className="row g-3">
            <Campo label="Nº de diárias" obrigatorio col="col-md-3"><input type="number" step="0.5" min="0.5" className="form-control" required {...bindNum('numeroDiarias')} /></Campo>
            <Campo label="Valor unitário (R$)" obrigatorio col="col-md-3"><input type="number" step="0.01" min="0" className="form-control" required {...bindNum('valorUnitario')} /></Campo>
            <Campo label="Total" col="col-md-3"><input className="form-control" readOnly value={moeda(total)} /></Campo>
            <Campo label="Finalidade" col="col-12"><textarea className="form-control" rows={3} maxLength={2000} {...bind('finalidade')} /></Campo>
          </div>
        </Section>
        <AcoesForm cancelarTo={voltar} salvando={salvando} label={editando ? 'Salvar alterações' : 'Abrir solicitação'} dica="Clique num valor da tabela para preencher o valor unitário." />
      </Card></form>
    </>
  )
}

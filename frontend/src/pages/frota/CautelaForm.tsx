import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { CautelaRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { agoraHora, hojeIso } from '../../utils/format'

const vazio: CautelaRequest = { veiculoId: '', dataViagem: hojeIso(), destino: '', motivo: '', responsavel: '', assistido: '', horaSaida: agoraHora(), kmInicial: '', horaChegada: '', kmFinal: '', motoristaNome: '', rg: '', cpf: '', fone: '', lotacao: '', observacao: '' }

export default function CautelaForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['cautela', id], queryFn: () => api.frota.cautela(Number(id)), enabled: editando })
  const veiculos = useQuery({ queryKey: ['veiculos', false], queryFn: () => api.frota.veiculos(false) })
  const cat = useQuery({ queryKey: ['frota-catalogos'], queryFn: api.frota.catalogos })
  const inicial = useMemo<CautelaRequest>(() => { const c = existente.data; if (!c) return vazio; return { veiculoId: c.veiculoId, dataViagem: c.dataViagem, destino: c.destino, motivo: c.motivo, responsavel: c.responsavel ?? '', assistido: c.assistido ?? '', horaSaida: c.horaSaida?.substring(0, 5) ?? '', kmInicial: c.kmInicial ?? '', horaChegada: c.horaChegada?.substring(0, 5) ?? '', kmFinal: c.kmFinal ?? '', motoristaNome: c.motoristaNome, rg: c.rg ?? '', cpf: c.cpf ?? '', fone: c.fone ?? '', lotacao: c.lotacao ?? '', observacao: c.observacao ?? '' } }, [existente.data])
  const { valores, bind, bindNum, set } = useForm(inicial)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const veiculo = veiculos.data?.find(v => v.id === Number(valores.veiculoId))
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try { const r = editando ? await api.frota.atualizarCautela(Number(id), valores) : await api.frota.criarCautela(valores); qc.invalidateQueries({ queryKey: ['cautelas'] }); qc.invalidateQueries({ queryKey: ['veiculos'] }); toast.sucesso('Cautela salva.'); if (!editando && confirm('Cautela registrada. Abrir o termo de cautela em PDF?')) window.open(api.frota.termoUrl(r.id), '_blank'); navigate('/frota/cautelas') }
    catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  return (
    <>
      <PageHeader voltar="/frota/cautelas" titulo={editando ? 'Editar cautela' : 'Nova cautela'} subtitulo="Entrega de viatura a condutor com termo de responsabilidade." />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-truck-front" titulo="Viatura e viagem">
          <div className="row g-3">
            <Campo label="Veículo" obrigatorio col="col-md-4"><select className="form-select" required {...bindNum('veiculoId')} onChange={e => { set('veiculoId', e.target.value ? Number(e.target.value) : ''); const v = veiculos.data?.find(x => x.id === Number(e.target.value)); if (v?.kmAtual && !valores.kmInicial) set('kmInicial', v.kmAtual) }}><option value="">Selecione</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}{v.naRua ? ' (na rua)' : ''}</option>)}</select></Campo>
            <Campo label="Data" obrigatorio col="col-md-2"><input type="date" className="form-control" required {...bind('dataViagem')} /></Campo>
            <Campo label="Hora de saída" col="col-md-2"><input type="time" className="form-control" {...bind('horaSaida')} /></Campo>
            <Campo label="Km de saída" col="col-md-2" hint={veiculo?.kmAtual ? `Km atual: ${veiculo.kmAtual}` : undefined}><input type="number" min="0" className="form-control" {...bindNum('kmInicial')} /></Campo>
            <Campo label="Motivo" obrigatorio col="col-md-2"><select className="form-select" required {...bind('motivo')}><option value="">Selecione</option>{cat.data?.motivos.map(m => <option key={m}>{m}</option>)}</select></Campo>
            <Campo label="Destino" obrigatorio col="col-md-6"><input className="form-control" required {...bind('destino')} /></Campo>
            <Campo label="Responsável (titular)" col="col-md-3"><input className="form-control" {...bind('responsavel')} /></Campo>
            <Campo label="Assistido" col="col-md-3"><input className="form-control" {...bind('assistido')} /></Campo>
            {editando && <><Campo label="Hora de chegada" col="col-md-2"><input type="time" className="form-control" {...bind('horaChegada')} /></Campo><Campo label="Km de chegada" col="col-md-2"><input type="number" min="0" className="form-control" {...bindNum('kmFinal')} /></Campo></>}
          </div>
        </Section>
        <Section icon="bi-person-vcard" titulo="Condutor" desc="Dados que constam no termo de cautela.">
          <div className="row g-3">
            <Campo label="Nome do condutor" obrigatorio col="col-md-5"><input className="form-control" required {...bind('motoristaNome')} /></Campo>
            <Campo label="RG" obrigatorio col="col-md-2"><input className="form-control" required {...bind('rg')} /></Campo>
            <Campo label="CPF" obrigatorio col="col-md-2"><input className="form-control" required maxLength={14} {...bind('cpf')} /></Campo>
            <Campo label="Telefone" obrigatorio col="col-md-3"><input className="form-control" required {...bind('fone')} /></Campo>
            <Campo label="Lotação" obrigatorio col="col-md-4"><select className="form-select" required {...bind('lotacao')}><option value="">Selecione</option>{cat.data?.lotacoes.map(m => <option key={m}>{m}</option>)}</select></Campo>
            <Campo label="Observação" col="col-md-8"><input className="form-control" {...bind('observacao')} /></Campo>
          </div>
        </Section>
        <AcoesForm cancelarTo="/frota/cautelas" salvando={salvando} />
      </Card></form>
    </>
  )
}

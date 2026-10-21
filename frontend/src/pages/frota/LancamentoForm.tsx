import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { Abastecimento, AbastecimentoRequest, Deslocamento, DeslocamentoRequest, Manutencao, ManutencaoRequest } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { agoraHora, hojeIso, numero } from '../../utils/format'

type Tipo = 'abastecimento' | 'manutencao' | 'deslocamento'
const titulos: Record<Tipo, string> = { abastecimento: 'Abastecimento', manutencao: 'Manutenção / revisão', deslocamento: 'Saída de viatura' }
const icones: Record<Tipo, string> = { abastecimento: 'bi-fuel-pump', manutencao: 'bi-wrench-adjustable', deslocamento: 'bi-signpost-2' }
type Form = AbastecimentoRequest & ManutencaoRequest & DeslocamentoRequest

export default function LancamentoForm({ tipo }: { tipo: Tipo }) {
  const { id, lancamentoId } = useParams()
  const editando = !!lancamentoId
  const { usuario, temPerfil, podeAbastecer } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const motorista = temPerfil('MOTORISTA')
  // Quem não gere a frota (motorista ou permissão extra de abastecer, ex.: GERENTE) lança com NF e com o próprio nome.
  const gestorFrota = temPerfil('ADMINISTRADOR', 'FISCAL', 'PRESIDENTE')
  const preencheProprioNome = motorista || (podeAbastecer && !gestorFrota)
  const veiculos = useQuery({ queryKey: ['veiculos', false], queryFn: () => api.frota.veiculos(false) })
  const cat = useQuery({ queryKey: ['frota-catalogos'], queryFn: api.frota.catalogos })
  // edição: busca o lançamento pela listagem paginada filtrada (sem endpoint GET individual)
  const existente = useQuery({
    queryKey: ['lancamento', tipo, lancamentoId], enabled: editando,
    queryFn: async () => {
      const lid = Number(lancamentoId)
      const lista = tipo === 'abastecimento' ? await api.frota.abastecimentos({}, 0, 500) : tipo === 'manutencao' ? await api.frota.manutencoes({}, 0, 500) : await api.frota.deslocamentos({}, 0, 500)
      const achado = (lista.content as { id: number }[]).find(x => x.id === lid)
      if (!achado) throw new Error('Lançamento não encontrado.')
      return achado as Abastecimento | Manutencao | Deslocamento
    },
  })
  const veiculoId = editando ? existente.data?.veiculoId : Number(id)
  const veiculo = veiculos.data?.find(v => v.id === veiculoId)
  const inicial = useMemo<Form>(() => {
    const base: Form = { veiculoId: veiculoId ?? '', data: hojeIso(), dataViagem: hojeIso(), km: '', combustivel: veiculo?.combustivelPadrao ?? '', quantidadeLitros: '', valor: '', postoCidade: veiculo?.cidade ?? '', motorista: preencheProprioNome ? (usuario?.nomeCompleto ?? '') : '', observacao: '', servico: '', descricao: '', fornecedor: '', notaFiscal: '', proximaRevisaoKm: '', proximaRevisaoData: '', destino: '', motivo: '', titular: '', assistido: '', horaSaida: agoraHora(), kmInicial: veiculo?.kmAtual ?? '', horaChegada: '', kmFinal: '', lotacao: '' }
    const e = existente.data as (Abastecimento & Manutencao & Deslocamento) | undefined
    if (!e) return base
    return { ...base, veiculoId: e.veiculoId, data: e.data ?? base.data, dataViagem: e.dataViagem ?? base.dataViagem, km: e.km ?? '', combustivel: e.combustivel ?? '', quantidadeLitros: e.quantidadeLitros ?? '', valor: e.valor ?? '', postoCidade: e.postoCidade ?? '', motorista: e.motorista ?? '', observacao: e.observacao ?? '', servico: e.servico ?? '', descricao: e.descricao ?? '', fornecedor: e.fornecedor ?? '', notaFiscal: e.notaFiscal ?? '', proximaRevisaoKm: e.proximaRevisaoKm ?? '', proximaRevisaoData: e.proximaRevisaoData ?? '', destino: e.destino ?? '', motivo: e.motivo ?? '', titular: e.titular ?? '', assistido: e.assistido ?? '', horaSaida: e.horaSaida?.substring(0, 5) ?? '', kmInicial: e.kmInicial ?? '', horaChegada: e.horaChegada?.substring(0, 5) ?? '', kmFinal: e.kmFinal ?? '', lotacao: e.lotacao ?? '' }
  }, [existente.data, veiculo?.id]) // eslint-disable-line react-hooks/exhaustive-deps
  const { valores, bind, bindNum, set } = useForm(inicial)
  const [nf, setNf] = useState<File | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [sugestoes, setSugestoes] = useState<string[]>([])
  useEffect(() => { if (tipo !== 'deslocamento' || !valores.titular || valores.titular.length < 2) { setSugestoes([]); return }; const t = setTimeout(() => api.frota.titulares(valores.titular!).then(setSugestoes).catch(() => {}), 250); return () => clearTimeout(t) }, [valores.titular, tipo])
  const precoLitro = valores.valor && valores.quantidadeLitros ? Number(valores.valor) / Number(valores.quantidadeLitros) : null
  const nfObrigatoria = !gestorFrota && !editando && tipo !== 'deslocamento'

  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null)
    if (nfObrigatoria && !nf) { setErro('Anexe a nota fiscal (PDF ou foto).'); return }
    setSalvando(true)
    try {
      const vid = Number(valores.veiculoId)
      let salvoId: number
      // Na criação a NF vai junto (multipart); na edição a NF nova é anexada depois.
      if (tipo === 'abastecimento') { const r: AbastecimentoRequest = { veiculoId: vid, data: valores.data, km: valores.km, combustivel: valores.combustivel, quantidadeLitros: valores.quantidadeLitros, valor: valores.valor, postoCidade: valores.postoCidade, motorista: valores.motorista, observacao: valores.observacao }; salvoId = editando ? (await api.frota.atualizarAbastecimento(Number(lancamentoId), r)).id : (await api.frota.abastecer(vid, r, nf)).id }
      else if (tipo === 'manutencao') { const r: ManutencaoRequest = { veiculoId: vid, data: valores.data, servico: valores.servico, km: valores.km, descricao: valores.descricao, valor: valores.valor, fornecedor: valores.fornecedor, notaFiscal: valores.notaFiscal, proximaRevisaoKm: valores.proximaRevisaoKm, proximaRevisaoData: valores.proximaRevisaoData, motorista: valores.motorista }; salvoId = editando ? (await api.frota.atualizarManutencao(Number(lancamentoId), r)).id : (await api.frota.manutencao(vid, r, nf)).id }
      else { const r: DeslocamentoRequest = { veiculoId: vid, dataViagem: valores.dataViagem, destino: valores.destino, motivo: valores.motivo, titular: valores.titular, assistido: valores.assistido, horaSaida: valores.horaSaida, kmInicial: valores.kmInicial, horaChegada: valores.horaChegada, kmFinal: valores.kmFinal, motorista: valores.motorista, lotacao: valores.lotacao, observacao: valores.observacao }; salvoId = editando ? (await api.frota.atualizarDeslocamento(Number(lancamentoId), r)).id : (await api.frota.deslocar(vid, r)).id }
      if (nf && editando && tipo !== 'deslocamento') await api.frota.anexarNf(tipo === 'abastecimento' ? 'abastecimentos' : 'manutencoes', salvoId, nf)
      qc.invalidateQueries({ queryKey: ['veiculo', String(vid)] }); qc.invalidateQueries({ queryKey: ['veiculo-historico', String(vid)] }); qc.invalidateQueries({ queryKey: ['veiculos'] }); qc.invalidateQueries({ queryKey: ['frota-resumo'] }); qc.invalidateQueries({ queryKey: ['frota-gestao'] }); qc.invalidateQueries({ queryKey: ['frota-lista'] })
      toast.sucesso(`${titulos[tipo]} registrado(a).`); navigate(`/frota/${vid}`)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  const voltar = veiculoId ? `/frota/${veiculoId}` : '/frota'
  return (
    <>
      <PageHeader voltar={voltar} titulo={`${editando ? 'Editar' : 'Registrar'} ${titulos[tipo].toLowerCase()}`} subtitulo={veiculo ? `${veiculo.placa} · ${veiculo.modelo} · km atual ${numero(veiculo.kmAtual)}` : undefined} />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon={icones[tipo]} titulo={titulos[tipo]}>
          <div className="row g-3">
            {!id && <Campo label="Veículo" obrigatorio col="col-md-6 col-xl-3"><select className="form-select" required {...bindNum('veiculoId')}><option value="">Selecione</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}</option>)}</select></Campo>}
            {tipo !== 'deslocamento' && <Campo label="Data" obrigatorio col="col-md-6 col-xl-3"><input type="date" className="form-control" required {...bind('data')} /></Campo>}
            {tipo === 'abastecimento' && <>
              <Campo label="Km no painel" col="col-md-6 col-xl-3"><input type="number" min="0" className="form-control" {...bindNum('km')} /></Campo>
              <Campo label="Combustível" obrigatorio col="col-md-6 col-xl-3"><select className="form-select" required {...bind('combustivel')}><option value="">Selecione</option>{cat.data?.combustiveis.map(c => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}</select></Campo>
              <Campo label="Litros" col="col-md-6 col-xl-3"><input type="number" step="0.001" min="0" className="form-control" {...bindNum('quantidadeLitros')} /></Campo>
              <Campo label="Valor (R$)" obrigatorio col="col-md-6 col-xl-3" hint={precoLitro ? `≈ R$ ${precoLitro.toFixed(3)}/L` : undefined}><input type="number" step="0.01" min="0" className="form-control" required {...bindNum('valor')} /></Campo>
              <Campo label="Posto / cidade" col="col-md-6 col-xl-3"><input className="form-control" {...bind('postoCidade')} /></Campo>
              <Campo label="Motorista" obrigatorio col="col-md-6 col-xl-3"><input className="form-control" required {...bind('motorista')} readOnly={motorista} /></Campo>
              <Campo label="Observação" col="col-12"><input className="form-control" {...bind('observacao')} /></Campo>
            </>}
            {tipo === 'manutencao' && <>
              <Campo label="Serviço" obrigatorio col="col-md-6 col-xl-3"><select className="form-select" required {...bind('servico')}><option value="">Selecione</option>{cat.data?.servicos.map(c => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}</select></Campo>
              <Campo label="Km" col="col-md-6 col-xl-3"><input type="number" min="0" className="form-control" {...bindNum('km')} /></Campo>
              <Campo label="Valor (R$)" col="col-md-6 col-xl-3"><input type="number" step="0.01" min="0" className="form-control" {...bindNum('valor')} /></Campo>
              <Campo label="Oficina / fornecedor" col="col-md-6 col-xl-3"><input className="form-control" {...bind('fornecedor')} /></Campo>
              <Campo label="Nº da NF" col="col-md-6 col-xl-3"><input className="form-control" {...bind('notaFiscal')} /></Campo>
              <Campo label="Descrição do serviço" col="col-md-6"><input className="form-control" {...bind('descricao')} /></Campo>
              <Campo label="Responsável pelo registro" obrigatorio col="col-md-6 col-xl-3"><input className="form-control" required {...bind('motorista')} readOnly={motorista} /></Campo>
              <Campo label="Próxima revisão (km)" col="col-md-6 col-xl-3" hint="Gera alerta quando o km atual passar deste valor."><input type="number" min="0" className="form-control" {...bindNum('proximaRevisaoKm')} /></Campo>
              <Campo label="Próxima revisão (data)" col="col-md-6 col-xl-3"><input type="date" className="form-control" {...bind('proximaRevisaoData')} /></Campo>
            </>}
            {tipo === 'deslocamento' && <>
              <Campo label="Data" obrigatorio col="col-md-6 col-xl-3"><input type="date" className="form-control" required {...bind('dataViagem')} /></Campo>
              <Campo label="Hora de saída" col="col-md-6 col-xl-3"><input type="time" className="form-control" {...bind('horaSaida')} /></Campo>
              <Campo label="Km de saída" col="col-md-6 col-xl-3"><input type="number" min="0" className="form-control" {...bindNum('kmInicial')} /></Campo>
              <Campo label="Motivo" obrigatorio col="col-md-6 col-xl-3"><select className="form-select" required {...bind('motivo')}><option value="">Selecione</option>{cat.data?.motivos.map(m => <option key={m}>{m}</option>)}</select></Campo>
              <Campo label="Lotação" col="col-md-6 col-xl-3"><select className="form-select" {...bind('lotacao')}><option value="">—</option>{cat.data?.lotacoes.map(m => <option key={m}>{m}</option>)}</select></Campo>
              <Campo label="Destino" obrigatorio col="col-md-6"><input className="form-control" required {...bind('destino')} /></Campo>
              <Campo label="Motorista" obrigatorio col="col-md-6 col-xl-3"><input className="form-control" required {...bind('motorista')} readOnly={motorista} /></Campo>
              <Campo label="Titular" col="col-md-6"><input className="form-control" list="dl-titulares" {...bind('titular')} onChange={e => set('titular', e.target.value)} /><datalist id="dl-titulares">{sugestoes.map(s => <option key={s} value={s} />)}</datalist></Campo>
              <Campo label="Assistido / passageiro" col="col-md-6"><input className="form-control" {...bind('assistido')} /></Campo>
              {editando && <><Campo label="Hora de chegada" col="col-md-6 col-xl-3"><input type="time" className="form-control" {...bind('horaChegada')} /></Campo><Campo label="Km de chegada" col="col-md-6 col-xl-3"><input type="number" min="0" className="form-control" {...bindNum('kmFinal')} /></Campo></>}
              <Campo label="Observação" col="col-12"><input className="form-control" {...bind('observacao')} /></Campo>
            </>}
          </div>
        </Section>
        {tipo !== 'deslocamento' && <Section icon="bi-paperclip" titulo="Nota fiscal" desc={nfObrigatoria ? 'Obrigatória: PDF ou foto do cupom/nota.' : 'PDF ou foto do cupom/nota (opcional para gestores).'} semBorda>
          <input type="file" className="form-control" accept="application/pdf,image/*" capture="environment" required={nfObrigatoria} onChange={e => setNf(e.target.files?.[0] ?? null)} />
          {editando && (existente.data as Abastecimento)?.possuiNotaFiscal && <div className="form-text">Já existe uma nota anexada ({(existente.data as Abastecimento).nfNomeOriginal}). Enviar outra substitui a atual.</div>}
        </Section>}
        <AcoesForm cancelarTo={voltar} salvando={salvando} label={editando ? 'Salvar' : 'Registrar'} />
      </Card></form>
    </>
  )
}

import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import { abrirArquivo } from '../../api/http'
import type { SeguroRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { hojeIso } from '../../utils/format'

/** Mesmo dia/mês do ano seguinte, menos um dia (vigência anual padrão). */
function umAnoDepois(iso: string) {
  const d = new Date(iso + 'T12:00:00'); d.setFullYear(d.getFullYear() + 1); d.setDate(d.getDate() - 1)
  return d.toISOString().substring(0, 10)
}
function diaSeguinte(iso: string) { const d = new Date(iso + 'T12:00:00'); d.setDate(d.getDate() + 1); return d.toISOString().substring(0, 10) }

export default function SeguroForm() {
  const { id } = useParams()
  const editando = !!id
  const [params] = useSearchParams()
  const veiculoParam = params.get('veiculoId')
  const renovar = params.get('renovar')
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['seguro', id], queryFn: () => api.frota.seguro(Number(id)), enabled: editando })
  const anterior = useQuery({ queryKey: ['seguro', renovar], queryFn: () => api.frota.seguro(Number(renovar)), enabled: !editando && !!renovar })
  const veiculos = useQuery({ queryKey: ['veiculos', false], queryFn: () => api.frota.veiculos(false) })
  const inicial = useMemo<SeguroRequest>(() => {
    const s = existente.data
    if (s) return { veiculoId: s.veiculoId, seguradora: s.seguradora, numeroApolice: s.numeroApolice, corretor: s.corretor ?? '', inicioVigencia: s.inicioVigencia, fimVigencia: s.fimVigencia, valorPremio: s.valorPremio ?? '', franquia: s.franquia ?? '', coberturas: s.coberturas ?? '', telefoneAssistencia: s.telefoneAssistencia ?? '', observacao: s.observacao ?? '' }
    const a = anterior.data
    // Renovação: copia seguradora, corretor, coberturas e assistência; a vigência começa no dia seguinte ao fim da anterior.
    if (a) { const inicio = diaSeguinte(a.fimVigencia); return { veiculoId: a.veiculoId, seguradora: a.seguradora, numeroApolice: '', corretor: a.corretor ?? '', inicioVigencia: inicio, fimVigencia: umAnoDepois(inicio), valorPremio: '', franquia: a.franquia ?? '', coberturas: a.coberturas ?? '', telefoneAssistencia: a.telefoneAssistencia ?? '', observacao: '' } }
    const hoje = hojeIso()
    return { veiculoId: veiculoParam ? Number(veiculoParam) : '', seguradora: '', numeroApolice: '', corretor: '', inicioVigencia: hoje, fimVigencia: umAnoDepois(hoje), valorPremio: '', franquia: '', coberturas: '', telefoneAssistencia: '', observacao: '' }
  }, [existente.data, anterior.data, veiculoParam])
  const { valores, bind, bindNum, set } = useForm(inicial)
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const voltar = valores.veiculoId ? `/frota/${valores.veiculoId}?aba=seguro` : '/frota/seguros'

  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try {
      if (editando) { await api.frota.atualizarSeguro(Number(id), valores); if (arquivo) await api.frota.anexarApolice(Number(id), arquivo) }
      else await api.frota.criarSeguro(valores, arquivo)
      qc.invalidateQueries({ queryKey: ['seguros'] }); qc.invalidateQueries({ queryKey: ['seguro'] }); qc.invalidateQueries({ queryKey: ['seguros-alertas'] })
      toast.sucesso('Seguro salvo.'); navigate(voltar)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if ((editando && existente.isLoading) || (renovar && anterior.isLoading)) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  return (
    <>
      <PageHeader voltar={voltar} titulo={editando ? 'Editar seguro' : renovar ? 'Renovar seguro' : 'Novo seguro'} subtitulo="Apólice de seguro do veículo. Cada renovação é uma apólice nova; o histórico fica guardado." />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-shield-check" titulo="Apólice">
          <div className="row g-3">
            <Campo label="Veículo" obrigatorio col="col-md-6 col-xl-4"><select className="form-select" required {...bindNum('veiculoId')}><option value="">Selecione</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}</option>)}</select></Campo>
            <Campo label="Seguradora" obrigatorio col="col-md-6 col-xl-4"><input className="form-control" required maxLength={120} {...bind('seguradora')} /></Campo>
            <Campo label="Nº da apólice" obrigatorio col="col-md-6 col-xl-4"><input className="form-control" required maxLength={60} {...bind('numeroApolice')} /></Campo>
            <Campo label="Início da vigência" obrigatorio col="col-md-6 col-xl-3"><input type="date" className="form-control" required value={valores.inicioVigencia} onChange={e => { const ini = e.target.value; set('inicioVigencia', ini); if (ini && !editando) set('fimVigencia', umAnoDepois(ini)) }} /></Campo>
            <Campo label="Fim da vigência" obrigatorio col="col-md-6 col-xl-3"><input type="date" className="form-control" required min={valores.inicioVigencia} {...bind('fimVigencia')} /></Campo>
            <Campo label="Prêmio (valor pago, R$)" col="col-md-6 col-xl-3"><input type="number" min="0" step="0.01" className="form-control" {...bindNum('valorPremio')} /></Campo>
            <Campo label="Franquia (R$)" col="col-md-6 col-xl-3"><input type="number" min="0" step="0.01" className="form-control" {...bindNum('franquia')} /></Campo>
            <Campo label="Corretor" col="col-md-6"><input className="form-control" maxLength={120} {...bind('corretor')} /></Campo>
            <Campo label="Telefone da assistência 24h" col="col-md-6" hint="Aparece para o motorista na ficha do veículo e no app."><input className="form-control" maxLength={40} placeholder="0800 000 0000" {...bind('telefoneAssistencia')} /></Campo>
            <Campo label="Coberturas" col="col-12"><textarea className="form-control" rows={2} maxLength={1000} placeholder="Casco, terceiros (danos materiais/corporais), vidros, carro reserva..." {...bind('coberturas')} /></Campo>
            <Campo label="Observação" col="col-12"><input className="form-control" maxLength={500} {...bind('observacao')} /></Campo>
          </div>
        </Section>
        <Section icon="bi-paperclip" titulo="Arquivo da apólice" desc="PDF ou foto da apólice (opcional)." semBorda>
          {editando && existente.data?.possuiApolice && <div className="small mb-2">Atual: <button type="button" className="btn btn-link btn-sm p-0" onClick={() => abrirArquivo(api.frota.apoliceUrl(Number(id)))}><i className="bi bi-file-earmark-pdf"></i> {existente.data.apoliceNomeOriginal ?? 'apólice'}</button>. Enviar outro substitui (o anterior fica guardado).</div>}
          <input type="file" className="form-control" accept="application/pdf,image/*" onChange={e => setArquivo(e.target.files?.[0] ?? null)} />
        </Section>
        <AcoesForm cancelarTo={voltar} salvando={salvando} />
      </Card></form>
    </>
  )
}

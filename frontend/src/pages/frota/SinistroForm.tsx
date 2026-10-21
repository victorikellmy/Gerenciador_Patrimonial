import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { SinistroRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { data, hojeIso, rotulo, SITUACOES_SINISTRO } from '../../utils/format'

export default function SinistroForm() {
  const { id } = useParams()
  const editando = !!id
  const [params] = useSearchParams()
  const veiculoParam = params.get('veiculoId')
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['sinistro', id], queryFn: () => api.frota.sinistro(Number(id)), enabled: editando })
  const veiculos = useQuery({ queryKey: ['veiculos', true], queryFn: () => api.frota.veiculos(true) })
  const inicial = useMemo<SinistroRequest>(() => {
    const s = existente.data
    if (s) return { veiculoId: s.veiculoId, seguroId: s.seguroId ?? '', dataOcorrencia: s.dataOcorrencia, numeroSinistro: s.numeroSinistro ?? '', descricao: s.descricao, localOcorrencia: s.localOcorrencia ?? '', condutor: s.condutor ?? '', valorPrejuizo: s.valorPrejuizo ?? '', valorIndenizado: s.valorIndenizado ?? '', situacao: s.situacao, observacao: s.observacao ?? '' }
    return { veiculoId: veiculoParam ? Number(veiculoParam) : '', seguroId: '', dataOcorrencia: hojeIso(), numeroSinistro: '', descricao: '', localOcorrencia: '', condutor: '', valorPrejuizo: '', valorIndenizado: '', situacao: 'ABERTO', observacao: '' }
  }, [existente.data, veiculoParam])
  const { valores, bind, bindNum, set } = useForm(inicial)
  const apolices = useQuery({ queryKey: ['seguros', { veiculoId: valores.veiculoId }], queryFn: () => api.frota.seguros({ veiculoId: Number(valores.veiculoId) }), enabled: !!valores.veiculoId })
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const voltar = valores.veiculoId ? `/frota/${valores.veiculoId}?aba=seguro` : '/frota/seguros?aba=sinistros'
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try {
      if (editando) await api.frota.atualizarSinistro(Number(id), valores); else await api.frota.criarSinistro(valores)
      qc.invalidateQueries({ queryKey: ['sinistros'] }); qc.invalidateQueries({ queryKey: ['sinistro'] }); qc.invalidateQueries({ queryKey: ['seguros-alertas'] })
      toast.sucesso('Sinistro salvo.'); navigate(voltar)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  return (
    <>
      <PageHeader voltar={voltar} titulo={editando ? 'Editar sinistro' : 'Registrar sinistro'} subtitulo="Ocorrência acionada no seguro: colisão, roubo, vidro, terceiros..." />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-car-front" titulo="Ocorrência">
          <div className="row g-3">
            <Campo label="Veículo" obrigatorio col="col-md-6 col-xl-4"><select className="form-select" required value={valores.veiculoId} onChange={e => { set('veiculoId', e.target.value ? Number(e.target.value) : ''); set('seguroId', '') }}><option value="">Selecione</option>{veiculos.data?.map(v => <option key={v.id} value={v.id}>{v.placa} · {v.modelo}</option>)}</select></Campo>
            <Campo label="Data da ocorrência" obrigatorio col="col-md-6 col-xl-4"><input type="date" className="form-control" required max={hojeIso()} {...bind('dataOcorrencia')} /></Campo>
            <Campo label="Apólice acionada" col="col-md-12 col-xl-4" hint="Em branco: usa a apólice que cobria a data da ocorrência."><select className="form-select" {...bindNum('seguroId')} disabled={!valores.veiculoId}><option value="">Automático</option>{apolices.data?.map(s => <option key={s.id} value={s.id}>{s.seguradora} · {s.numeroApolice} ({data(s.inicioVigencia)} a {data(s.fimVigencia)})</option>)}</select></Campo>
            <Campo label="O que aconteceu" obrigatorio col="col-12"><textarea className="form-control" rows={2} required maxLength={1000} {...bind('descricao')} /></Campo>
            <Campo label="Local" col="col-md-6"><input className="form-control" maxLength={160} {...bind('localOcorrencia')} /></Campo>
            <Campo label="Condutor" col="col-md-6"><input className="form-control" maxLength={160} {...bind('condutor')} /></Campo>
          </div>
        </Section>
        <Section icon="bi-clipboard-check" titulo="Acompanhamento com a seguradora" semBorda>
          <div className="row g-3">
            <Campo label="Situação" obrigatorio col="col-md-6 col-xl-3"><select className="form-select" required {...bind('situacao')}>{SITUACOES_SINISTRO.map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Campo>
            <Campo label="Nº do sinistro (protocolo)" col="col-md-6 col-xl-3"><input className="form-control" maxLength={60} {...bind('numeroSinistro')} /></Campo>
            <Campo label="Prejuízo estimado (R$)" col="col-md-6 col-xl-3"><input type="number" min="0" step="0.01" className="form-control" {...bindNum('valorPrejuizo')} /></Campo>
            <Campo label="Valor indenizado (R$)" col="col-md-6 col-xl-3"><input type="number" min="0" step="0.01" className="form-control" {...bindNum('valorIndenizado')} /></Campo>
            <Campo label="Observação" col="col-12"><input className="form-control" maxLength={500} {...bind('observacao')} /></Campo>
          </div>
        </Section>
        <AcoesForm cancelarTo={voltar} salvando={salvando} />
      </Card></form>
    </>
  )
}

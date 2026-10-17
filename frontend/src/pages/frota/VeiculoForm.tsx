import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { VeiculoRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { rotulo, CONSERVACOES } from '../../utils/format'

const vazio: VeiculoRequest = { patrimonioId: '', placa: '', modelo: '', marca: '', chassi: '', renavam: '', ano: '', cidade: '', combustivelPadrao: '', kmAtual: '', numeroTombo: '', dataCompra: '', valorCompra: '', conservacao: '', notaFiscal: '', observacao: '', lotacaoId: '', responsavelId: '' }

export default function VeiculoForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const ficha = useQuery({ queryKey: ['veiculo', id], queryFn: () => api.frota.veiculo(Number(id)), enabled: editando })
  const disponiveis = useQuery({ queryKey: ['patrimonios-disponiveis'], queryFn: api.frota.patrimoniosDisponiveis, enabled: !editando })
  const lotacoes = useQuery({ queryKey: ['lotacoes-todas'], queryFn: api.lotacoes.todas })
  const responsaveis = useQuery({ queryKey: ['responsaveis-todos'], queryFn: api.responsaveis.todos })
  const cat = useQuery({ queryKey: ['frota-catalogos'], queryFn: api.frota.catalogos })
  const inicial = useMemo<VeiculoRequest>(() => {
    const v = ficha.data?.veiculo
    if (!v) return vazio
    return { ...vazio, placa: v.placa, modelo: v.modelo, marca: v.marca ?? '', chassi: v.chassi ?? '', renavam: v.renavam ?? '', ano: v.ano ?? '', cidade: v.cidade ?? '', combustivelPadrao: v.combustivelPadrao ?? '', kmAtual: v.kmAtual ?? '', numeroTombo: v.numeroTombo ?? '', dataCompra: v.dataCompra ?? '', valorCompra: v.valorCompra ?? '', conservacao: v.conservacao ?? '', notaFiscal: v.notaFiscal ?? '', observacao: v.observacao ?? '', lotacaoId: v.lotacaoId ?? '', responsavelId: v.responsavelId ?? '' }
  }, [ficha.data])
  const { valores, bind, bindNum } = useForm(inicial)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const vinculando = !editando && !!valores.patrimonioId
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try {
      const salvo = editando ? await api.frota.atualizarVeiculo(Number(id), valores) : await api.frota.criarVeiculo(valores)
      qc.invalidateQueries({ queryKey: ['veiculos'] }); qc.invalidateQueries({ queryKey: ['veiculo', String(salvo.id)] }); qc.invalidateQueries({ queryKey: ['frota-gestao'] })
      toast.sucesso('Veículo salvo.'); navigate(`/frota/${salvo.id}`)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && ficha.isLoading) return <Carregando />
  if (ficha.error) return <Erro erro={ficha.error} />
  const voltar = editando ? `/frota/${id}` : '/frota'
  return (
    <>
      <PageHeader voltar={voltar} titulo={editando ? 'Editar veículo' : 'Novo veículo'} subtitulo="Todo veículo é um bem patrimonial da categoria VEÍCULO." />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        {!editando && <Section icon="bi-link-45deg" titulo="Vínculo patrimonial" desc="Vincule a um bem já cadastrado como veículo ou deixe em branco para criar o bem agora.">
          <div className="row g-3"><Campo label="Bem patrimonial existente" col="col-md-8"><select className="form-select" {...bindNum('patrimonioId')}><option value="">Criar novo bem patrimonial</option>{disponiveis.data?.map(p => <option key={p.id} value={p.id}>{p.numeroTombo ? `${p.numeroTombo} · ` : ''}{p.descricao} ({p.lotacaoUpm} · {p.lotacaoNome})</option>)}</select></Campo></div>
        </Section>}
        <Section icon="bi-truck-front" titulo="Identificação do veículo">
          <div className="row g-3">
            <Campo label="Placa" obrigatorio col="col-md-2"><input className="form-control text-uppercase" required maxLength={20} {...bind('placa')} /></Campo>
            <Campo label="Modelo" obrigatorio col="col-md-4"><input className="form-control" required maxLength={60} {...bind('modelo')} /></Campo>
            <Campo label="Marca" col="col-md-3"><input className="form-control" {...bind('marca')} /></Campo>
            <Campo label="Ano" col="col-md-3"><input className="form-control" maxLength={9} placeholder="2022/2023" {...bind('ano')} /></Campo>
            <Campo label="Chassi" col="col-md-4"><input className="form-control" {...bind('chassi')} /></Campo>
            <Campo label="Renavam" col="col-md-3"><input className="form-control" {...bind('renavam')} /></Campo>
            <Campo label="Cidade" col="col-md-3"><input className="form-control" {...bind('cidade')} /></Campo>
            <Campo label="Combustível padrão" col="col-md-2"><select className="form-select" {...bind('combustivelPadrao')}><option value="">—</option>{cat.data?.combustiveis.map(c => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}</select></Campo>
            <Campo label="Km atual" col="col-md-2"><input type="number" min="0" className="form-control" {...bindNum('kmAtual')} /></Campo>
          </div>
        </Section>
        {!vinculando && <Section icon="bi-box-seam" titulo="Dados patrimoniais" desc={editando ? 'Atualiza o bem patrimonial vinculado.' : 'Usados para criar o bem patrimonial do veículo.'}>
          <div className="row g-3">
            <Campo label="Nº do tombo" col="col-md-2"><input className="form-control" {...bind('numeroTombo')} /></Campo>
            <Campo label="Lotação" obrigatorio={!editando} col="col-md-4"><select className="form-select" required={!editando} {...bindNum('lotacaoId')}><option value="">Selecione</option>{lotacoes.data?.map(l => <option key={l.id} value={l.id}>{l.upm} · {l.nome}</option>)}</select></Campo>
            <Campo label="Responsável" obrigatorio={!editando} col="col-md-4"><select className="form-select" required={!editando} {...bindNum('responsavelId')}><option value="">Selecione</option>{responsaveis.data?.map(r => <option key={r.id} value={r.id}>{r.nomeCompleto}</option>)}</select></Campo>
            <Campo label="Conservação" col="col-md-2"><select className="form-select" {...bind('conservacao')}><option value="">—</option>{CONSERVACOES.map(c => <option key={c} value={c}>{rotulo(c)}</option>)}</select></Campo>
            <Campo label="Data da compra" col="col-md-3"><input type="date" className="form-control" {...bind('dataCompra')} /></Campo>
            <Campo label="Valor (R$)" col="col-md-3"><input type="number" step="0.01" min="0" className="form-control" {...bindNum('valorCompra')} /></Campo>
            <Campo label="Nota fiscal" col="col-md-3"><input className="form-control" {...bind('notaFiscal')} /></Campo>
          </div>
        </Section>}
        <Section icon="bi-card-text" titulo="Observações" semBorda><textarea className="form-control" rows={3} {...bind('observacao')} /></Section>
        <AcoesForm cancelarTo={voltar} salvando={salvando} />
      </Card></form>
    </>
  )
}

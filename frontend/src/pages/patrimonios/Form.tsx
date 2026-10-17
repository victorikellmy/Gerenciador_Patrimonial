import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { PatrimonioRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { rotulo, CONSERVACOES } from '../../utils/format'

const vazio: PatrimonioRequest = { numeroTombo: '', descricao: '', categoria: '', subcategoria: '', dataCompra: '', valorCompra: '', conservacao: '', notaFiscal: '', valorRecuperavel: '', conclusaoImpairment: '', observacao: '', linkReferencia: '', lotacaoId: '', responsavelId: '' } as unknown as PatrimonioRequest

export default function PatrimonioForm() {
  const { id, pendenciaId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const qc = useQueryClient()
  const editando = !!id
  const existente = useQuery({ queryKey: ['patrimonio', id], queryFn: () => api.patrimonios.buscar(Number(id)), enabled: editando })
  const preparado = useQuery({ queryKey: ['pendencia-preparar', pendenciaId], queryFn: () => api.pendencias.preparar(Number(pendenciaId)), enabled: !!pendenciaId })
  const pendencia = useQuery({ queryKey: ['pendencia', pendenciaId], queryFn: () => api.pendencias.buscar(Number(pendenciaId)), enabled: !!pendenciaId })
  const lotacoes = useQuery({ queryKey: ['lotacoes-todas'], queryFn: api.lotacoes.todas })
  const responsaveis = useQuery({ queryKey: ['responsaveis-todos'], queryFn: api.responsaveis.todos })
  const categorias = useQuery({ queryKey: ['categorias'], queryFn: api.catalogos.categorias })
  const subcategorias = useQuery({ queryKey: ['subcategorias'], queryFn: api.catalogos.subcategorias })

  const inicial = useMemo<PatrimonioRequest>(() => {
    const p = existente.data
    if (p) return { ...vazio, numeroTombo: p.numeroTombo ?? '', descricao: p.descricao, categoria: p.categoria ?? '', subcategoria: p.subcategoria ?? '', dataCompra: p.dataCompra ?? '', valorCompra: p.valorCompra ?? '', conservacao: p.conservacao ?? '', notaFiscal: p.notaFiscal ?? '', valorRecuperavel: p.valorRecuperavel ?? '', conclusaoImpairment: p.conclusaoImpairment ?? '', observacao: p.observacao ?? '', linkReferencia: p.linkReferencia ?? '', lotacaoId: p.lotacaoId ?? '', responsavelId: p.responsavelId ?? '' } as PatrimonioRequest
    if (preparado.data) { const r = preparado.data; return { ...vazio, ...Object.fromEntries(Object.entries(r).map(([k, v]) => [k, v ?? ''])) } as PatrimonioRequest }
    return vazio
  }, [existente.data, preparado.data])
  const { valores, bind, bindNum, set } = useForm(inicial)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  // responsável filtrado pela lotação escolhida (fallback: todos)
  const respFiltrados = useMemo(() => {
    const lista = responsaveis.data ?? []
    const daLotacao = lista.filter(r => r.lotacaoId === Number(valores.lotacaoId))
    return daLotacao.length ? daLotacao : lista
  }, [responsaveis.data, valores.lotacaoId])
  useEffect(() => {
    const lot = lotacoes.data?.find(l => l.id === Number(valores.lotacaoId))
    if (lot?.responsavelAtualId && !valores.responsavelId) set('responsavelId', lot.responsavelAtualId)
  }, [valores.lotacaoId]) // eslint-disable-line react-hooks/exhaustive-deps

  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    const req = { ...valores, valorCompra: (valores.valorCompra as unknown) === '' ? null : valores.valorCompra, valorRecuperavel: (valores.valorRecuperavel as unknown) === '' ? null : valores.valorRecuperavel, conservacao: valores.conservacao || undefined, lotacaoId: valores.lotacaoId || undefined, responsavelId: valores.responsavelId || undefined, dataCompra: valores.dataCompra || undefined } as PatrimonioRequest
    try {
      let salvo
      if (pendenciaId) salvo = await api.pendencias.patrimoniar(Number(pendenciaId), req)
      else if (editando) salvo = await api.patrimonios.atualizar(Number(id), req)
      else salvo = await api.patrimonios.criar(req)
      qc.invalidateQueries({ queryKey: ['patrimonios'] }); qc.invalidateQueries({ queryKey: ['me-resumo'] }); qc.invalidateQueries({ queryKey: ['pendencia', pendenciaId] })
      toast.sucesso(pendenciaId ? 'Bem patrimoniado com sucesso.' : 'Bem salvo com sucesso.')
      if (pendenciaId) {
        const restante = (pendencia.data?.quantidadeRestante ?? 1) - 1
        navigate(restante > 0 ? `/pendencias/${pendenciaId}` : `/patrimonios/${salvo.id}`)
      } else navigate(`/patrimonios/${salvo.id}`)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }

  if ((editando && existente.isLoading) || (pendenciaId && preparado.isLoading)) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  const voltar = pendenciaId ? `/pendencias/${pendenciaId}` : editando ? `/patrimonios/${id}` : '/patrimonios'
  return (
    <>
      <PageHeader voltar={voltar} titulo={pendenciaId ? 'Patrimoniar bem recebido' : editando ? 'Editar bem' : 'Novo bem'} subtitulo={pendenciaId && pendencia.data ? `Pendência #${pendencia.data.id} · ${pendencia.data.quantidadeRestante} unidade(s) restante(s) · NF ${pendencia.data.numeroNotaFiscal ?? '—'}` : 'Informe os dados do bem patrimonial.'} />
      <form onSubmit={salvar}>
        <Card>
          {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
          <Section icon="bi-box-seam" titulo="Identificação" desc="Dados básicos do bem.">
            <div className="row g-3">
              <Campo label="Nº do tombo" col="col-md-3"><input className="form-control" {...bind('numeroTombo')} placeholder="Ex: 001234" /></Campo>
              <Campo label="Descrição" obrigatorio col="col-md-9"><input className="form-control" {...bind('descricao')} required maxLength={255} /></Campo>
              <Campo label="Categoria" col="col-md-4"><input className="form-control" list="dl-cat" {...bind('categoria')} /><datalist id="dl-cat">{categorias.data?.map(c => <option key={c} value={c} />)}</datalist></Campo>
              <Campo label="Subcategoria" col="col-md-4"><input className="form-control" list="dl-sub" {...bind('subcategoria')} /><datalist id="dl-sub">{subcategorias.data?.map(c => <option key={c} value={c} />)}</datalist></Campo>
              <Campo label="Conservação" col="col-md-4"><select className="form-select" {...bind('conservacao')}><option value="">Selecione</option>{CONSERVACOES.map(c => <option key={c} value={c}>{rotulo(c)}</option>)}</select></Campo>
            </div>
          </Section>
          <Section icon="bi-geo-alt" titulo="Localização" desc="Onde o bem está e quem responde por ele.">
            <div className="row g-3">
              <Campo label="Lotação" col="col-md-6"><select className="form-select" {...bindNum('lotacaoId')}><option value="">Selecione</option>{lotacoes.data?.map(l => <option key={l.id} value={l.id}>{l.upm} · {l.nome}</option>)}</select></Campo>
              <Campo label="Responsável" col="col-md-6"><select className="form-select" {...bindNum('responsavelId')}><option value="">Selecione</option>{respFiltrados.map(r => <option key={r.id} value={r.id}>{r.nomeCompleto}</option>)}</select></Campo>
            </div>
          </Section>
          <Section icon="bi-cash-coin" titulo="Aquisição e valor" desc="Base para depreciação e valor contábil.">
            <div className="row g-3">
              <Campo label="Data da compra" col="col-md-3"><input type="date" className="form-control" {...bind('dataCompra')} /></Campo>
              <Campo label="Valor de compra (R$)" col="col-md-3"><input type="number" step="0.01" min="0" className="form-control" {...bindNum('valorCompra')} /></Campo>
              <Campo label="Nota fiscal" col="col-md-3"><input className="form-control" {...bind('notaFiscal')} /></Campo>
              <Campo label="Valor recuperável (R$)" col="col-md-3" hint="Para teste de impairment."><input type="number" step="0.01" min="0" className="form-control" {...bindNum('valorRecuperavel')} /></Campo>
              <Campo label="Conclusão do impairment" col="col-12"><input className="form-control" {...bind('conclusaoImpairment')} /></Campo>
            </div>
          </Section>
          <Section icon="bi-card-text" titulo="Complementos" semBorda>
            <div className="row g-3">
              <Campo label="Link de referência" col="col-md-6"><input type="url" className="form-control" {...bind('linkReferencia')} placeholder="https://" /></Campo>
              <Campo label="Observação" col="col-12"><textarea className="form-control" rows={3} {...bind('observacao')} /></Campo>
            </div>
          </Section>
          <AcoesForm cancelarTo={voltar} salvando={salvando} label={pendenciaId ? 'Patrimoniar' : 'Salvar'} />
        </Card>
      </form>
    </>
  )
}

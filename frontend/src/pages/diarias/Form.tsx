import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { DiariaRequest, NivelDiaria, Profissional, TipoDiaria } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { dataHora, moeda, numero } from '../../utils/format'

/** Data de hoje no fuso do navegador (toISOString usaria UTC e viraria o dia à noite). */
const hojeLocal = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().substring(0, 10)

const vazio: DiariaRequest = {
  profissionalId: '', numeroRequerimento: '', dataRequerimento: hojeLocal(),
  nome: '', matricula: '', cargo: '', lotacao: '', cpf: '', rg: '', orgaoExpedidor: '', nivelId: '',
  origem: '', itinerario: '', partida: '', horaSaida: '', retorno: '', horaChegada: '', meioTransporte: '', recurso: '', finalidade: '',
  banco: '', agencia: '', contaCorrente: '', pix: '', tipoDiaria: '', valorUnitario: '',
}

// Mesmos formatos do formulário de referência: o que não é permitido nem chega a ser digitado.
const filtros = {
  semNumeros: (v: string) => v.replace(/[^\p{L}\p{M} .,'’\-–/()]/gu, ''),
  identificador: (v: string) => v.replace(/[^0-9/-]/g, ''),
  requerimento: (v: string) => v.replace(/[^0-9/]/g, ''),
  banco: (v: string) => v.replace(/\D/g, '').slice(0, 3),
  agencia: (v: string) => v.replace(/[^0-9-]/g, ''),
  conta: (v: string) => v.replace(/[^0-9xX-]/g, '').toUpperCase(),
  pix: (v: string) => v.replace(/\s/g, ''),
  cpf: (v: string) => {
    const d = v.replace(/\D/g, '').slice(0, 11)
    return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1-$2')
  },
}

const TIPOS: [TipoDiaria, string][] = [['CAPITAL', 'Capital'], ['INTERIOR_ESTADO', 'Interior do Estado'], ['INTERIOR_OUTRO_ESTADO', 'Interior de outro Estado']]

function valorDaTabela(nivel: NivelDiaria | undefined, tipo: TipoDiaria | '') {
  if (!nivel || !tipo) return undefined
  return Number(tipo === 'CAPITAL' ? nivel.valorCapitais : tipo === 'INTERIOR_ESTADO' ? nivel.valorInteriorEstado : nivel.valorInteriorOutroEstado)
}

const duracao = (min: number) => `${Math.floor(min / 60)}h ${min % 60}min`

/** Busca no cadastro quem já recebeu diária: escolher um resultado preenche o formulário inteiro. */
function BuscaProfissional({ aoEscolher }: { aoEscolher: (p: Profissional) => void }) {
  const [termo, setTermo] = useState('')
  const [buscado, setBuscado] = useState('')
  const [aberto, setAberto] = useState(false)
  useEffect(() => { const t = setTimeout(() => setBuscado(termo.trim()), 250); return () => clearTimeout(t) }, [termo])
  const q = useQuery({ queryKey: ['profissionais-busca', buscado], queryFn: () => api.diarias.buscarProfissionais(buscado), enabled: buscado.length >= 2 })
  return (
    <div className="position-relative">
      <div className="input-group">
        <span className="input-group-text"><i className="bi bi-search"></i></span>
        <input className="form-control" type="search" autoComplete="off" aria-label="Buscar profissional no cadastro"
          placeholder="Buscar quem já recebeu diária: nome, CPF ou matrícula"
          value={termo} onChange={e => { setTermo(e.target.value); setAberto(true) }} onFocus={() => setAberto(true)} onBlur={() => setTimeout(() => setAberto(false), 150)} />
      </div>
      {aberto && buscado.length >= 2 && (
        <div className="list-group position-absolute w-100 shadow" style={{ zIndex: 1050, maxHeight: 320, overflowY: 'auto' }}>
          {q.isLoading && <div className="list-group-item small text-muted">Buscando…</div>}
          {q.error && <div className="list-group-item small text-danger">Não foi possível buscar no cadastro.</div>}
          {q.data?.length === 0 && <div className="list-group-item small text-muted">Ninguém encontrado. Preencha os dados abaixo: o cadastro é criado quando o requerimento for salvo.</div>}
          {q.data?.map(p => (
            <button type="button" key={p.id} className="list-group-item list-group-item-action" onMouseDown={e => e.preventDefault()}
              onClick={() => { aoEscolher(p); setTermo(''); setAberto(false) }}>
              <div className="fw-semibold">{p.nome}</div>
              <div className="small text-muted">{[p.matricula && `Matrícula ${p.matricula}`, p.cpf, p.cargo ?? p.nivelDescricao, p.lotacao].filter(Boolean).join(' · ')}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function DiariaForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['diaria', id], queryFn: () => api.diarias.buscar(Number(id)), enabled: editando })
  const niveis = useQuery({ queryKey: ['niveis', 'ativos'], queryFn: () => api.diarias.niveis(true) })
  const inicial = useMemo<DiariaRequest>(() => {
    const d = existente.data
    if (!d) return vazio
    return {
      profissionalId: d.profissionalId, numeroRequerimento: d.numeroRequerimento ?? '', dataRequerimento: d.dataSolicitacao,
      nome: d.profissionalNome, matricula: d.profissionalMatricula ?? '', cargo: d.profissionalCargo ?? '', lotacao: d.profissionalLotacao ?? '',
      cpf: d.profissionalCpf ?? '', rg: d.profissionalRg ?? '', orgaoExpedidor: d.profissionalOrgaoExpedidor ?? '', nivelId: d.profissionalNivelId ?? '',
      origem: d.origem ?? '', itinerario: d.itinerario, partida: d.partida, horaSaida: d.horaSaida?.substring(0, 5) ?? '', retorno: d.retorno, horaChegada: d.horaChegada?.substring(0, 5) ?? '',
      meioTransporte: d.meioTransporte, recurso: d.recurso, finalidade: d.finalidade ?? '',
      banco: d.banco ?? '', agencia: d.agencia ?? '', contaCorrente: d.contaCorrente ?? '', pix: d.pix ?? '',
      tipoDiaria: d.tipoDiaria ?? '', valorUnitario: d.valorUnitario,
    }
  }, [existente.data])
  const { valores, set, setValores } = useForm(inicial)
  const [carregado, setCarregado] = useState<Profissional | null>(null)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  /** Liga um campo de texto ao formulário, aplicando o filtro de caracteres quando houver. */
  const campo = (nome: keyof DiariaRequest, filtro?: (v: string) => string) => ({
    name: String(nome),
    value: (valores[nome] ?? '') as string | number,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => set(nome, filtro ? filtro(e.target.value) : e.target.value),
  })

  const nivel = niveis.data?.find(n => n.id === Number(valores.nivelId))
  const sugerido = valorDaTabela(nivel, valores.tipoDiaria)

  const preencher = (p: Profissional, aviso = true) => {
    const nivelDoCadastro = niveis.data?.find(n => n.id === p.nivelId)
    setValores(v => ({
      ...v, profissionalId: p.id, nome: p.nome, matricula: p.matricula ?? '', cargo: p.cargo ?? '', lotacao: p.lotacao ?? '', cpf: p.cpf,
      rg: p.rg ?? '', orgaoExpedidor: p.orgaoExpedidor ?? '', nivelId: p.nivelId ?? '',
      banco: p.banco ?? '', agencia: p.agencia ?? '', contaCorrente: p.contaCorrente ?? '', pix: p.pix ?? '',
      valorUnitario: valorDaTabela(nivelDoCadastro, v.tipoDiaria) ?? v.valorUnitario,
    }))
    setCarregado(p)
    if (aviso) toast.sucesso(`Dados de ${p.nome} carregados do cadastro.`)
  }
  const limparProfissional = () => {
    setValores(v => ({ ...v, profissionalId: '', nome: '', matricula: '', cargo: '', lotacao: '', cpf: '', rg: '', orgaoExpedidor: '', nivelId: '', banco: '', agencia: '', contaCorrente: '', pix: '' }))
    setCarregado(null)
  }
  /** Digitou um CPF que já está no cadastro: traz os dados sem precisar usar a busca. */
  const conferirCpf = async () => {
    const digitos = valores.cpf.replace(/\D/g, '')
    if (valores.profissionalId || digitos.length !== 11) return
    try {
      const achados = await api.diarias.buscarProfissionais(digitos)
      const p = achados.find(x => x.cpf.replace(/\D/g, '') === digitos)
      if (p) preencher(p)
    } catch { /* a busca é só uma ajuda; o servidor confere o CPF ao salvar */ }
  }

  const prontoParaCalcular = !!(valores.partida && valores.horaSaida && valores.retorno && valores.horaChegada)
  const calc = useQuery({
    queryKey: ['diaria-calculo', valores.partida, valores.horaSaida, valores.retorno, valores.horaChegada],
    queryFn: () => api.diarias.calculo(valores.partida, valores.horaSaida, valores.retorno, valores.horaChegada),
    enabled: prontoParaCalcular, retry: false, staleTime: Infinity,
  })
  const c = prontoParaCalcular && !calc.error ? calc.data : undefined
  const unitario = Number(valores.valorUnitario) || 0
  const total = c && unitario > 0 ? Math.round(c.quantidade * unitario * 100) / 100 : undefined
  const erroCalculo = prontoParaCalcular && calc.error ? (calc.error instanceof Error ? calc.error.message : 'Datas ou horários inválidos.') : null
  const semDiaria = !!c && c.quantidade <= 0
  const mensagem = erroCalculo ?? (!prontoParaCalcular ? 'Informe as datas e os horários para apurar a quantidade de diárias.'
    : calc.isLoading ? 'Calculando…'
    : semDiaria ? 'O afastamento informado não gera diária: tem menos de 24 horas e não abrange 12h nem 18h.'
    : !valores.tipoDiaria ? 'Quantidade apurada. Selecione o tipo de diária para calcular o total.'
    : unitario <= 0 ? 'Quantidade apurada. Informe o valor unitário aprovado para o tipo de diária selecionado.'
    : 'Cálculo concluído. Confira identificação, missão e dados bancários antes de salvar.')
  const calculoOk = !!c && !semDiaria && !!valores.tipoDiaria && unitario > 0

  const salvar = async (e: FormEvent) => {
    e.preventDefault()
    if (erroCalculo || semDiaria) { setErro(mensagem); return }
    setErro(null); setSalvando(true)
    try {
      const d = editando ? await api.diarias.atualizar(Number(id), valores) : await api.diarias.criar(valores)
      for (const chave of [['diarias'], ['diarias-contagens'], ['diaria', String(d.id)], ['diaria-historico', String(d.id)], ['me-resumo'], ['profissionais'], ['profissionais-busca']]) qc.invalidateQueries({ queryKey: chave })
      toast.sucesso(editando ? 'Requerimento atualizado.' : `Requerimento nº ${d.numeroRequerimento ?? d.id} aberto. Baixe o PDF para enviar pelo SGD.`)
      navigate(`/diarias/${d.id}`)
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.'); window.scrollTo({ top: 0, behavior: 'smooth' }) } finally { setSalvando(false) }
  }

  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  const voltar = editando ? `/diarias/${id}` : '/diarias'
  const Q = 'col-md-6 col-xl-3'
  const bancoAntigo = carregado?.dadosBancarios && !carregado.banco ? carregado.dadosBancarios : null
  return (
    <>
      <PageHeader voltar={voltar} titulo={editando ? 'Editar requerimento de diárias' : 'Novo requerimento de diárias'}
        subtitulo="Anexo II da Portaria 001/2019. Ao salvar, o sistema gera o requerimento em PDF para envio pelo SGD." />
      {editando && existente.data && existente.data.status !== 'SOLICITADO' && (
        <div className="alert alert-warning"><i className="bi bi-exclamation-triangle-fill"></i><div>Esta diária está <strong>{existente.data.status === 'DEFERIDO' ? 'deferida' : 'indeferida'}</strong>. Ao salvar alterações ela volta para <strong>Solicitada</strong> e precisa de nova decisão do Presidente. Os valores de antes e depois ficam no histórico.</div></div>
      )}
      <form onSubmit={salvar}><Card style={{ overflow: 'visible' }}>
        {erro && <div className="alert alert-danger m-3 mb-0" role="alert"><i className="bi bi-exclamation-triangle-fill"></i> {erro}</div>}

        <Section icon="bi-person-vcard" titulo="1. Identificação" desc="Quem já recebeu diária é encontrado pela busca. Quem ainda não tem cadastro passa a ter quando este requerimento for salvo.">
          <div className="row g-3">
            <div className="col-12">
              <BuscaProfissional aoEscolher={p => preencher(p)} />
              {valores.profissionalId !== '' && (
                <div className="small text-muted mt-2">
                  <i className="bi bi-person-check-fill text-success"></i> Usando o cadastro de <strong>{valores.nome}</strong>. O que for alterado aqui atualiza o cadastro para as próximas solicitações.{' '}
                  <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={limparProfissional}>Trocar de profissional</button>
                </div>
              )}
            </div>
            <Campo label="Número do requerimento" col={Q} hint={editando ? undefined : 'Em branco, o sistema numera em sequência.'}><input className="form-control" maxLength={20} inputMode="numeric" placeholder="Gerado automaticamente" title="Use somente algarismos e barra (/)." {...campo('numeroRequerimento', filtros.requerimento)} /></Campo>
            <Campo label="Data do requerimento" obrigatorio col={Q}><input type="date" className="form-control" required {...campo('dataRequerimento')} /></Campo>
            <Campo label="Matrícula funcional" obrigatorio col={Q}><input className="form-control" required maxLength={30} title="Use somente algarismos, barra (/) e hífen (-)." {...campo('matricula', filtros.identificador)} /></Campo>
            <Campo label="CPF" obrigatorio col={Q}><input className="form-control" required maxLength={14} inputMode="numeric" placeholder="000.000.000-00" pattern="\d{3}\.\d{3}\.\d{3}-\d{2}" title="CPF com 11 dígitos" {...campo('cpf', filtros.cpf)} onBlur={conferirCpf} /></Campo>
            <Campo label="Profissional" obrigatorio col="col-md-6"><input className="form-control" required maxLength={160} title="Use letras e sinais de pontuação, sem números." {...campo('nome', filtros.semNumeros)} /></Campo>
            <Campo label="Cargo, posto ou função" obrigatorio col="col-md-6"><input className="form-control" required maxLength={120} {...campo('cargo')} /></Campo>
            <Campo label="Lotação" obrigatorio col="col-md-6"><input className="form-control" required maxLength={60} {...campo('lotacao')} /></Campo>
            <Campo label="RG" col={Q}><input className="form-control" maxLength={30} {...campo('rg')} /></Campo>
            <Campo label="Órgão expedidor" obrigatorio col={Q}><input className="form-control" required maxLength={30} placeholder="Ex.: SSP/TO" title="Use letras e sinais de pontuação, sem números." {...campo('orgaoExpedidor', filtros.semNumeros)} /></Campo>
          </div>
        </Section>

        <Section icon="bi-geo-alt" titulo="2. Missão e deslocamento">
          <div className="row g-3">
            <Campo label="Origem" obrigatorio col="col-md-6"><input className="form-control" required maxLength={120} placeholder="Ex.: Palmas/TO" title="Use letras e sinais de pontuação, sem números." {...campo('origem', filtros.semNumeros)} /></Campo>
            <Campo label="Destino / itinerário" obrigatorio col="col-md-6"><input className="form-control" required maxLength={200} placeholder="Ex.: Araguaína/TO" {...campo('itinerario')} /></Campo>
            <Campo label="Data de saída" obrigatorio col={Q}><input type="date" className="form-control" required {...campo('partida')} /></Campo>
            <Campo label="Horário de saída" obrigatorio col={Q}><input type="time" className="form-control" required {...campo('horaSaida')} /></Campo>
            <Campo label="Data de retorno à origem" obrigatorio col={Q}><input type="date" className="form-control" required min={valores.partida || undefined} {...campo('retorno')} /></Campo>
            <Campo label="Horário de retorno" obrigatorio col={Q}><input type="time" className="form-control" required {...campo('horaChegada')} /></Campo>
            <Campo label="Meio de transporte" obrigatorio col="col-md-6"><select className="form-select" required {...campo('meioTransporte')}><option value="">Selecione</option><option value="AEREO">Aéreo</option><option value="RODOVIARIO">Rodoviário</option><option value="VEICULO_OFICIAL">Veículo da Fundação</option><option value="OUTRO">Outro</option></select></Campo>
            <Campo label="Fonte do recurso" obrigatorio col="col-md-6"><select className="form-select" required {...campo('recurso')}><option value="">Selecione</option><option value="PROPRIO">Próprio</option><option value="CONVENIO">Convênio</option><option value="OUTRA">Outra</option></select></Campo>
            <Campo label="Finalidade da viagem" obrigatorio col="col-12" hint={`${valores.finalidade.length}/350 caracteres`}><textarea className="form-control" rows={3} required maxLength={350} {...campo('finalidade')} /></Campo>
            {bancoAntigo && <div className="col-12"><div className="alert alert-info mb-0 small"><i className="bi bi-info-circle"></i><div>Dados bancários do cadastro antigo: <strong>{bancoAntigo}</strong>. Informe-os nos campos abaixo; eles ficam gravados para as próximas solicitações.</div></div></div>}
            <Campo label="Banco" obrigatorio col={Q}><input className="form-control" required inputMode="numeric" maxLength={3} pattern="[0-9]{3}" placeholder="Código de 3 dígitos" title="Informe exatamente três dígitos." {...campo('banco', filtros.banco)} /></Campo>
            <Campo label="Agência" obrigatorio col={Q}><input className="form-control" required inputMode="numeric" maxLength={15} pattern="[0-9]+(-[0-9]+)?" placeholder="Agência e dígito" title="Use algarismos e hífen para separar o dígito." {...campo('agencia', filtros.agencia)} /></Campo>
            <Campo label="Conta corrente" obrigatorio col={Q}><input className="form-control" required maxLength={20} pattern="[0-9]+(-([0-9]+|[Xx]))?" placeholder="Conta e dígito" title="Use algarismos e, se for o dígito verificador, X ao final após o hífen." {...campo('contaCorrente', filtros.conta)} /></Campo>
            <Campo label="PIX" obrigatorio col={Q}><input className="form-control" required maxLength={120} placeholder="Chave PIX" title="Use letras, números e símbolos, sem espaços." {...campo('pix', filtros.pix)} /></Campo>
          </div>
        </Section>

        <Section icon="bi-calculator" titulo="3. Apuração da quantidade e do valor" semBorda>
          <div className="row g-3">
            <Campo label="Tipo de diária" obrigatorio col={Q}><select className="form-select" required name="tipoDiaria" value={valores.tipoDiaria}
              onChange={e => { const tipo = e.target.value as TipoDiaria | ''; setValores(v => ({ ...v, tipoDiaria: tipo, valorUnitario: valorDaTabela(nivel, tipo) ?? v.valorUnitario })) }}>
              <option value="">Selecione o tipo de diária</option>{TIPOS.map(([v, r]) => <option key={v} value={v}>{r}</option>)}</select></Campo>
            <Campo label="Nível na tabela de valores" col={Q} hint="Opcional: preenche o valor unitário conforme a tabela."><select className="form-select" name="nivelId" value={valores.nivelId}
              onChange={e => { const nid = e.target.value === '' ? '' : Number(e.target.value); const n = niveis.data?.find(x => x.id === nid); setValores(v => ({ ...v, nivelId: nid, valorUnitario: valorDaTabela(n, v.tipoDiaria) ?? v.valorUnitario })) }}>
              <option value="">Sem nível (valor digitado)</option>{niveis.data?.map(n => <option key={n.id} value={n.id}>{n.descricao}</option>)}</select></Campo>
            <Campo label="Valor unitário da diária integral (R$)" obrigatorio col={Q}
              hint={sugerido !== undefined && sugerido !== unitario ? <>Tabela: {moeda(sugerido)}. <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={() => set('valorUnitario', sugerido)}>Usar este valor</button></> : undefined}>
              <input type="number" step="0.01" min="0.01" className="form-control" required placeholder="Valor conforme o tipo" name="valorUnitario" value={valores.valorUnitario} onChange={e => set('valorUnitario', e.target.value === '' ? '' : Number(e.target.value))} /></Campo>
          </div>

          <div className={`small fw-semibold mt-3 ${erroCalculo || semDiaria ? 'text-danger' : calculoOk ? 'text-success' : 'text-muted'}`} role="status" aria-live="polite">{mensagem}</div>
          {c?.avisoPortaria && <div className="alert alert-warning mt-2 mb-0 small"><i className="bi bi-exclamation-triangle-fill"></i> {c.avisoPortaria}</div>}

          <div className="row g-3 mt-1">
            <div className="col-md-4"><div className="kpi-label">Quantidade de diárias</div><div className="kpi-value">{c ? numero(c.quantidade, 1) : '—'}</div></div>
            <div className="col-md-4"><div className="kpi-label">Valor unitário</div><div className="kpi-value">{unitario > 0 ? moeda(unitario) : '—'}</div></div>
            <div className="col-md-4"><div className="kpi-label">Valor total das diárias a serem concedidas</div><div className="kpi-value text-success">{total !== undefined ? moeda(total) : '—'}</div></div>
          </div>

          <div className="fw-semibold mt-4 mb-2">Memória de cálculo</div>
          <div className="table-responsive table-livre"><table className="table table-compacta mb-0">
            <thead><tr><th>Componente</th><th>Critério aplicado</th><th className="text-end">Diárias</th></tr></thead>
            <tbody>{!c ? <tr><td colSpan={3} className="text-muted">Aguardando datas e horários válidos.</td></tr> : <>
              <tr><td>Períodos completos de 24 horas</td><td>{c.integrais} período(s) × 1 diária</td><td className="text-end">{numero(c.integrais, 1)}</td></tr>
              <tr><td>Alimentação adicional</td><td>{c.restoMin === 0 ? 'Não há intervalo restante: nenhuma parcela adicional é computada.'
                : c.refeicoes.length ? `${c.refeicoes.map(r => dataHora(r)).join('; ')} (limite de 0,5)` : 'Sem coincidência com 12h ou 18h no intervalo restante.'}
                {c.restoMin > 0 && <div className="small text-muted">Intervalo restante: {dataHora(c.inicioResto)} até {dataHora(`${valores.retorno}T${valores.horaChegada}`)}</div>}</td><td className="text-end">{numero(c.alimentacao, 1)}</td></tr>
              <tr><td colSpan={2} className="fw-semibold">Total · duração do afastamento: {duracao(c.duracaoMin)}</td><td className="text-end fw-semibold">{numero(c.quantidade, 1)}</td></tr>
            </>}</tbody>
          </table></div>

          <details className="mt-3 small">
            <summary className="fw-semibold" style={{ cursor: 'pointer' }}>Regras e orientações</summary>
            <ul className="mt-2 mb-0">
              <li>Cada período completo de 24 horas corresponde a 1 diária: 50% para alimentação e 50% para hospedagem.</li>
              <li>No intervalo restante, a presença do horário de almoço (12h) ou de jantar (18h) gera 0,5 diária de alimentação. Mesmo abrangendo os dois, o acréscimo fica limitado a 0,5.</li>
              <li>Para afastamento inferior a 24 horas valem os mesmos critérios do intervalo restante.</li>
              <li>Os horários exatos de 12h e 18h são incluídos nos limites do intervalo.</li>
              <li>Datas e horários devem ser informados no fuso de referência da origem.</li>
              <li>O sistema não aplica descontos por despesas custeadas por terceiros. Essas situações exigem avaliação antes da autorização.</li>
            </ul>
          </details>
        </Section>
        <AcoesForm cancelarTo={voltar} salvando={salvando} label={editando ? 'Salvar alterações' : 'Salvar e gerar requerimento'} dica="Todos os campos com * são obrigatórios. A quantidade de diárias é calculada pelas datas e horários." />
      </Card></form>
    </>
  )
}

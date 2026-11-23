import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api'
import type { ProfissionalRequest } from '../../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../../components/ui'
import { moeda } from '../../utils/format'

const vazio: ProfissionalRequest = { nome: '', matricula: '', lotacao: '', nivelId: '', cpf: '', rg: '', orgaoExpedidor: '', dadosBancarios: '', cargo: '', banco: '', agencia: '', contaCorrente: '', pix: '' }

export default function ProfissionalForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['profissional', id], queryFn: () => api.diarias.profissional(Number(id)), enabled: editando })
  const niveis = useQuery({ queryKey: ['niveis', 'ativos'], queryFn: () => api.diarias.niveis(true) })
  const inicial = useMemo<ProfissionalRequest>(() => { const p = existente.data; if (!p) return vazio; return { nome: p.nome, matricula: p.matricula ?? '', lotacao: p.lotacao ?? '', nivelId: p.nivelId ?? '', cpf: p.cpf, rg: p.rg ?? '', orgaoExpedidor: p.orgaoExpedidor ?? '', dadosBancarios: p.dadosBancarios ?? '', cargo: p.cargo ?? '', banco: p.banco ?? '', agencia: p.agencia ?? '', contaCorrente: p.contaCorrente ?? '', pix: p.pix ?? '' } }, [existente.data])
  const { valores, bind, bindNum } = useForm(inicial)
  const nivel = niveis.data?.find(n => n.id === Number(valores.nivelId))
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    const corpo = { ...valores, nivelId: valores.nivelId === '' ? null : valores.nivelId } as unknown as ProfissionalRequest
    try { if (editando) await api.diarias.atualizarProfissional(Number(id), corpo); else await api.diarias.criarProfissional(corpo); qc.invalidateQueries({ queryKey: ['profissionais'] }); qc.invalidateQueries({ queryKey: ['profissional', id] }); toast.sucesso('Profissional salvo.'); navigate('/diarias/profissionais') }
    catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  return (
    <>
      <PageHeader voltar="/diarias/profissionais" titulo={editando ? 'Editar profissional' : 'Novo profissional'} />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-person-vcard" titulo="Identificação">
          <div className="row g-3">
            <Campo label="Nome" obrigatorio col="col-md-6"><input className="form-control" required maxLength={160} {...bind('nome')} /></Campo>
            <Campo label="Matrícula" col="col-md-6 col-xl-3"><input className="form-control" {...bind('matricula')} /></Campo>
            <Campo label="Lotação" col="col-md-6 col-xl-3"><input className="form-control" maxLength={60} {...bind('lotacao')} /></Campo>
            <Campo label="Cargo, posto ou função" col="col-md-6"><input className="form-control" maxLength={120} placeholder="Como sai no requerimento de diárias" {...bind('cargo')} /></Campo>
            <Campo label="CPF" obrigatorio col="col-md-6 col-xl-3"><input className="form-control" required maxLength={14} placeholder="000.000.000-00" {...bind('cpf')} /></Campo>
            <Campo label="RG" col="col-md-6 col-xl-3"><input className="form-control" {...bind('rg')} /></Campo>
            <Campo label="Órgão expedidor" col="col-md-6 col-xl-3"><input className="form-control" {...bind('orgaoExpedidor')} /></Campo>
          </div>
        </Section>
        <Section icon="bi-table" titulo="Tabela de valores e dados bancários" desc="O nível é opcional: serve para sugerir o valor unitário no requerimento." semBorda>
          <div className="row g-3">
            <Campo label="Nível na tabela de valores" col="col-md-6"><select className="form-select" {...bindNum('nivelId')}><option value="">Sem nível definido</option>{niveis.data?.map(n => <option key={n.id} value={n.id}>{n.descricao}</option>)}</select></Campo>
            <div className="col-md-6"><label className="form-label">Valores da tabela</label><div className="small">{nivel ? <>Capitais <strong>{moeda(nivel.valorCapitais)}</strong> · Interior TO <strong>{moeda(nivel.valorInteriorEstado)}</strong> · Outro estado <strong>{moeda(nivel.valorInteriorOutroEstado)}</strong></> : <span className="text-muted">Selecione o nível para ver os valores.</span>}</div></div>
            <Campo label="Banco" col="col-md-6 col-xl-3"><input className="form-control" inputMode="numeric" maxLength={3} pattern="[0-9]{3}" title="Código do banco com três dígitos" placeholder="Código de 3 dígitos" {...bind('banco')} /></Campo>
            <Campo label="Agência" col="col-md-6 col-xl-3"><input className="form-control" maxLength={15} pattern="[0-9]+(-[0-9]+)?" title="Algarismos e, se necessário, hífen e dígito" placeholder="Agência e dígito" {...bind('agencia')} /></Campo>
            <Campo label="Conta corrente" col="col-md-6 col-xl-3"><input className="form-control" maxLength={20} pattern="[0-9]+(-([0-9]+|[Xx]))?" title="Algarismos e, se for o dígito verificador, X após o hífen" placeholder="Conta e dígito" {...bind('contaCorrente')} /></Campo>
            <Campo label="PIX" col="col-md-6 col-xl-3"><input className="form-control" maxLength={120} pattern="\S+" title="Chave PIX sem espaços" placeholder="Chave PIX" {...bind('pix')} /></Campo>
            {valores.dadosBancarios && <Campo label="Dados bancários (cadastro antigo)" col="col-12" hint="Texto livre herdado do sistema anterior. Os campos acima o substituem nos novos requerimentos."><input className="form-control" {...bind('dadosBancarios')} /></Campo>}
          </div>
        </Section>
        <AcoesForm cancelarTo="/diarias/profissionais" salvando={salvando} />
      </Card></form>
    </>
  )
}

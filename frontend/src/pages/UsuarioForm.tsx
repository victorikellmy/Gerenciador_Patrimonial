import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import type { Perfil, UsuarioRequest } from '../api/types'
import { AcoesForm, Campo, Card, Carregando, Erro, PageHeader, Section, useForm, useToast } from '../components/ui'
import { rotulo, PERFIS } from '../utils/format'

const vazio: UsuarioRequest = { nomeCompleto: '', login: '', senha: '', perfil: 'FISCAL', ativo: true, exigirTrocaSenha: true }
const descPerfil: Record<Perfil, string> = { ADMINISTRADOR: 'Acesso total, incluindo usuários, auditoria e importações.', FISCAL: 'Opera patrimônio, levantamentos, frota e diárias.', GERENTE: 'Somente leitura: painel da frota, patrimônios e relatórios.', PRESIDENTE: 'Defere ou indefere diárias; consulta patrimônio.', FINANCEIRO: 'Registra pagamento de diárias; consulta patrimônio.', MOTORISTA: 'Somente frota: saídas, abastecimentos e manutenções com nota fiscal.', INTEGRACAO: 'Conta técnica usada pelo Almoxarifado (API).' }

export default function UsuarioForm() {
  const { id } = useParams()
  const editando = !!id
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const existente = useQuery({ queryKey: ['usuario', id], queryFn: () => api.usuarios.buscar(Number(id)), enabled: editando })
  const inicial = useMemo<UsuarioRequest>(() => { const u = existente.data; if (!u) return vazio; return { nomeCompleto: u.nomeCompleto, login: u.login, senha: '', perfil: u.perfil, ativo: u.ativo, exigirTrocaSenha: u.senhaProvisoria } }, [existente.data])
  const { valores, bind, set } = useForm(inicial)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const salvar = async (e: FormEvent) => {
    e.preventDefault(); setErro(null); setSalvando(true)
    try { const req = { ...valores, senha: valores.senha || undefined }; if (editando) await api.usuarios.atualizar(Number(id), req); else await api.usuarios.criar(req); qc.invalidateQueries({ queryKey: ['usuarios'] }); qc.invalidateQueries({ queryKey: ['usuario', id] }); toast.sucesso('Usuário salvo.'); navigate('/usuarios') }
    catch (ex) { setErro(ex instanceof Error ? ex.message : 'Falha ao salvar.') } finally { setSalvando(false) }
  }
  if (editando && existente.isLoading) return <Carregando />
  if (existente.error) return <Erro erro={existente.error} />
  return (
    <>
      <PageHeader voltar="/usuarios" titulo={editando ? 'Editar usuário' : 'Novo usuário'} subtitulo="Novos usuários são obrigados a definir uma nova senha no primeiro acesso." />
      <form onSubmit={salvar}><Card>
        {erro && <div className="alert alert-danger m-3 mb-0">{erro}</div>}
        <Section icon="bi-person" titulo="Conta">
          <div className="row g-3">
            <Campo label="Nome completo" obrigatorio col="col-md-6"><input className="form-control" required {...bind('nomeCompleto')} /></Campo>
            <Campo label="Login" obrigatorio col="col-md-3"><input className="form-control" required autoComplete="off" {...bind('login')} /></Campo>
            <Campo label={editando ? 'Nova senha (opcional)' : 'Senha provisória'} obrigatorio={!editando} col="col-md-3" hint={editando ? 'Deixe em branco para manter.' : 'Mínimo 6 caracteres.'}><input type="password" className="form-control" autoComplete="new-password" required={!editando} minLength={6} {...bind('senha')} /></Campo>
          </div>
        </Section>
        <Section icon="bi-shield-lock" titulo="Perfil e situação" semBorda>
          <div className="row g-3">
            <Campo label="Perfil" obrigatorio col="col-md-4" hint={descPerfil[valores.perfil]}><select className="form-select" {...bind('perfil')}>{PERFIS.map(p => <option key={p} value={p}>{rotulo(p)}</option>)}</select></Campo>
            <div className="col-md-4"><div className="form-check form-switch mt-md-4 pt-md-2"><input className="form-check-input" type="checkbox" id="ativo" checked={valores.ativo} onChange={e => set('ativo', e.target.checked)} /><label className="form-check-label" htmlFor="ativo">Usuário ativo</label></div></div>
            <div className="col-md-4"><div className="form-check form-switch mt-md-4 pt-md-2"><input className="form-check-input" type="checkbox" id="troca" checked={valores.exigirTrocaSenha} onChange={e => set('exigirTrocaSenha', e.target.checked)} /><label className="form-check-label" htmlFor="troca">Exigir troca de senha no próximo acesso</label></div></div>
          </div>
        </Section>
        <AcoesForm cancelarTo="/usuarios" salvando={salvando} />
      </Card></form>
    </>
  )
}

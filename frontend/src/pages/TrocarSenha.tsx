import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth, destinoInicial } from '../auth/AuthContext'
import { Card, PageHeader, Section, useToast } from '../components/ui'

export default function TrocarSenha() {
  const [params] = useSearchParams()
  const obrigatoria = params.has('obrigatoria')
  const { usuario, recarregar } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [atual, setAtual] = useState('')
  const [nova, setNova] = useState('')
  const [conf, setConf] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    if (nova !== conf) { setErro('A confirmação não confere com a nova senha.'); return }
    setErro(null); setSalvando(true)
    try {
      await api.auth.trocarSenha(atual, nova, conf)
      await recarregar()
      toast.sucesso('Senha alterada com sucesso.')
      navigate(destinoInicial(usuario?.perfil), { replace: true })
    } catch (ex) { setErro(ex instanceof Error ? ex.message : 'Não foi possível alterar a senha.') } finally { setSalvando(false) }
  }

  return (
    <>
      <PageHeader titulo="Trocar senha" subtitulo={obrigatoria ? 'Primeiro acesso: defina uma nova senha para continuar.' : 'Altere a senha da sua conta.'} />
      <div className="row justify-content-center">
        <div className="col-lg-6">
          <Card>
            {obrigatoria && <div className="alert alert-warning m-3 mb-0"><i className="bi bi-shield-lock"></i> Sua senha é provisória. Por segurança, escolha uma nova senha antes de usar o sistema.</div>}
            <form onSubmit={enviar}>
              <Section icon="bi-key" titulo="Credenciais" semBorda>
                {erro && <div className="alert alert-danger py-2">{erro}</div>}
                <div className="row g-3">
                  <div className="col-12"><label className="form-label required">Senha atual</label><input type="password" className="form-control" autoComplete="current-password" value={atual} onChange={e => setAtual(e.target.value)} required /></div>
                  <div className="col-md-6"><label className="form-label required">Nova senha</label><input type="password" className="form-control" autoComplete="new-password" minLength={6} value={nova} onChange={e => setNova(e.target.value)} required /><div className="form-text">Mínimo de 6 caracteres.</div></div>
                  <div className="col-md-6"><label className="form-label required">Confirmar nova senha</label><input type="password" className="form-control" autoComplete="new-password" value={conf} onChange={e => setConf(e.target.value)} required /></div>
                </div>
              </Section>
              <div className="sticky-actions">
                <button className="btn btn-primary" disabled={salvando}>{salvando ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-check-lg"></i>} Salvar nova senha</button>
              </div>
            </form>
          </Card>
        </div>
      </div>
    </>
  )
}

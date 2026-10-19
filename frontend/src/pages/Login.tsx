import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth, destinoInicial } from '../auth/AuthContext'
import { BotaoBaixarApp } from '../components/BaixarApp'

export default function Login() {
  const { usuario, carregando, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [user, setUser] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (!carregando && usuario) return <Navigate to={destinoInicial(usuario.perfil)} replace />

  const entrar = async (e: FormEvent) => {
    e.preventDefault()
    setErro(null); setEnviando(true)
    try {
      const u = await login(user.trim(), senha)
      if (u.senhaProvisoria) navigate('/minha-conta/senha?obrigatoria', { replace: true })
      else navigate((location.state as { de?: string } | null)?.de || destinoInicial(u.perfil), { replace: true })
    } catch (ex) {
      setErro(ex instanceof Error ? ex.message : 'Usuário ou senha inválidos.')
    } finally { setEnviando(false) }
  }

  return (
    <div className="login-page">
      <div className="login-wrapper">
        <div className="brand-block">
          <div className="brand-logo"><img src="/images/logo-white.png" alt="Fundação Pró-Tocantins" /></div>
          <div className="brand-title">Gerenciador Patrimonial</div>
          <div className="brand-sub">Sistema de Gestão de Bens</div>
        </div>
        <div className="login-card">
          <div className="login-title"><i className="bi bi-person-lock me-2" style={{ color: 'var(--primary-600)' }}></i>Acesso ao sistema</div>
          {erro && <div className="alert alert-danger" role="alert"><i className="bi bi-exclamation-triangle-fill"></i> {erro}</div>}
          <form onSubmit={entrar}>
            <div className="mb-3">
              <label className="form-label">Login</label>
              <div className="input-group">
                <span className="input-group-text"><i className="bi bi-person"></i></span>
                <input type="text" className="form-control" autoFocus autoComplete="username" value={user} onChange={e => setUser(e.target.value)} required />
              </div>
            </div>
            <div className="mb-4">
              <label className="form-label">Senha</label>
              <div className="input-group">
                <span className="input-group-text"><i className="bi bi-key"></i></span>
                <input type="password" className="form-control" autoComplete="current-password" value={senha} onChange={e => setSenha(e.target.value)} required />
              </div>
            </div>
            <button className="btn btn-primary w-100" type="submit" disabled={enviando}>
              {enviando ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-box-arrow-in-right"></i>} Entrar
            </button>
          </form>
        </div>
        <BotaoBaixarApp variante="bloco" />
        <div className="login-footer">Fundação Pró-Tocantins · {new Date().getFullYear()}</div>
      </div>
    </div>
  )
}

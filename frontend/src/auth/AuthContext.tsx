import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api'
import { setUnauthorizedHandler } from '../api/http'
import type { Perfil, Usuario } from '../api/types'

interface AuthState {
  usuario: Usuario | null
  carregando: boolean
  login: (login: string, senha: string) => Promise<Usuario>
  logout: () => Promise<void>
  recarregar: () => Promise<void>
  temPerfil: (...perfis: Perfil[]) => boolean
  /** Registra abastecimentos: perfis que lançam na frota ou a permissão extra do usuário (ex.: GERENTE). */
  podeAbastecer: boolean
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [carregando, setCarregando] = useState(true)

  const recarregar = useCallback(async () => {
    try { setUsuario(await api.auth.me()) } catch { setUsuario(null) } finally { setCarregando(false) }
  }, [])

  useEffect(() => { recarregar() }, [recarregar])
  useEffect(() => { setUnauthorizedHandler(() => setUsuario(null)) }, [])

  const value = useMemo<AuthState>(() => ({
    usuario, carregando, recarregar,
    login: async (l, s) => { const u = await api.auth.login(l, s); setUsuario(u); return u },
    logout: async () => { try { await api.auth.logout() } finally { setUsuario(null) } },
    temPerfil: (...perfis) => !!usuario && perfis.includes(usuario.perfil),
    podeAbastecer: !!usuario && (['ADMINISTRADOR', 'FISCAL', 'PRESIDENTE', 'MOTORISTA'].includes(usuario.perfil) || !!usuario.podeAbastecer),
  }), [usuario, carregando, recarregar])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useAuth fora do AuthProvider')
  return ctx
}

/** Tela inicial de cada perfil (mesma regra do login do back-end). */
export function destinoInicial(perfil?: Perfil): string {
  switch (perfil) {
    case 'MOTORISTA': return '/frota'
    case 'GERENTE': return '/frota/gestao'
    case 'PRESIDENTE':
    case 'FINANCEIRO': return '/diarias'
    default: return '/'
  }
}

import { useEffect, useState } from 'react'

/** Endereço público do APK (servido pelo Spring em /downloads/**). */
export const APK_URL = '/downloads/AppPatrimonio.apk'

/** Mostra o botão só quando o APK está publicado no servidor (HEAD 200). */
function useApkDisponivel() {
  const [ok, setOk] = useState(false)
  useEffect(() => {
    let vivo = true
    fetch(APK_URL, { method: 'HEAD', cache: 'no-store' })
      .then(r => { if (vivo) setOk(r.ok) })
      .catch(() => { /* sem APK publicado: botão fica escondido */ })
    return () => { vivo = false }
  }, [])
  return ok
}

/** Botão "Baixar aplicativo Android (APK)". `variante="bloco"` ocupa a largura toda (tela de login). */
export function BotaoBaixarApp({ variante = 'inline' }: { variante?: 'inline' | 'bloco' }) {
  const disponivel = useApkDisponivel()
  if (!disponivel) return null

  const botao = (
    <a className={`btn btn-success${variante === 'bloco' ? ' w-100' : ''}`} href={APK_URL} download="AppPatrimonio.apk"
       title="Baixar o aplicativo da frota para celulares Android">
      <i className="bi bi-android2"></i> Baixar aplicativo Android (APK)
    </a>
  )
  if (variante === 'inline') return botao

  return (
    <div className="login-card mt-3 text-center">
      {botao}
      <div className="small text-muted mt-2">
        No celular, abra o arquivo baixado e permita a instalação de “fontes desconhecidas” quando o Android pedir.
      </div>
    </div>
  )
}

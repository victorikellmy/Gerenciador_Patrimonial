import { useEffect, useState } from 'react'

/** Endereço público do APK (servido pelo Spring em /downloads/**). */
export const APK_URL = '/downloads/AppPatrimonio.apk'

type InfoApk = { publicadoEm: Date | null; tamanho: number | null }

/**
 * Consulta o APK publicado (HEAD). Devolve null enquanto não houver arquivo no servidor.
 * A data de publicação (Last-Modified) entra no endereço e no nome do arquivo baixado: assim
 * nenhum cache entrega a versão antiga e o celular não confunde com um AppPatrimonio.apk
 * antigo que já esteja na pasta Downloads.
 */
function useApk() {
  const [info, setInfo] = useState<InfoApk | null>(null)
  useEffect(() => {
    let vivo = true
    fetch(APK_URL, { method: 'HEAD', cache: 'no-store' })
      .then(r => {
        if (!vivo || !r.ok) return
        const lm = r.headers.get('Last-Modified')
        const tam = r.headers.get('Content-Length')
        const data = lm ? new Date(lm) : null
        setInfo({ publicadoEm: data && !isNaN(data.getTime()) ? data : null, tamanho: tam ? Number(tam) : null })
      })
      .catch(() => { /* sem APK publicado: botão fica escondido */ })
    return () => { vivo = false }
  }, [])
  return info
}

const dois = (n: number) => String(n).padStart(2, '0')
const carimbo = (d: Date) => `${d.getFullYear()}${dois(d.getMonth() + 1)}${dois(d.getDate())}-${dois(d.getHours())}${dois(d.getMinutes())}`
const dataHora = (d: Date) => `${dois(d.getDate())}/${dois(d.getMonth() + 1)}/${d.getFullYear()} ${dois(d.getHours())}:${dois(d.getMinutes())}`

/** Botão "Baixar aplicativo Android (APK)". `variante="bloco"` ocupa a largura toda (tela de login). */
export function BotaoBaixarApp({ variante = 'inline' }: { variante?: 'inline' | 'bloco' }) {
  const info = useApk()
  if (!info) return null

  const versao = info.publicadoEm ? carimbo(info.publicadoEm) : String(info.tamanho ?? Date.now())
  const href = `${APK_URL}?v=${versao}`
  const nome = info.publicadoEm ? `AppPatrimonio-${versao}.apk` : 'AppPatrimonio.apk'
  const titulo = info.publicadoEm ? `Versão publicada em ${dataHora(info.publicadoEm)}` : 'Baixar o aplicativo da frota para celulares Android'

  const botao = (
    <a className={`btn btn-success${variante === 'bloco' ? ' w-100' : ''}`} href={href} download={nome} title={titulo}>
      <i className="bi bi-android2"></i> Baixar aplicativo Android (APK)
    </a>
  )
  if (variante === 'inline') return botao

  return (
    <div className="login-card mt-3 text-center">
      {botao}
      {info.publicadoEm && <div className="small fw-semibold mt-2">Versão publicada em {dataHora(info.publicadoEm)}</div>}
      <div className="small text-muted mt-1">
        No celular, abra o arquivo baixado (o mais recente na pasta Downloads) e permita a instalação de “fontes desconhecidas” quando o Android pedir.
      </div>
    </div>
  )
}

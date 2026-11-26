import { useAuth } from '../auth/AuthContext'
import { Card, PageHeader, Section } from '../components/ui'

/** Tela de quem entrou com a conta da rede mas ainda não está em nenhum grupo do Patrimônio. */
export default function AcessoPendente() {
  const { usuario } = useAuth()
  return (
    <>
      <PageHeader titulo={`Olá, ${usuario?.nomeCompleto ?? ''}`} subtitulo="Você entrou com a sua conta da rede da Fundação." />
      <div className="row justify-content-center"><div className="col-lg-7"><Card>
        <Section icon="bi-hourglass-split" titulo="Acesso aguardando liberação" semBorda>
          <p>Sua conta (<code>{usuario?.login}</code>) ainda não faz parte de nenhum grupo do Patrimônio, por isso nenhum módulo aparece no menu.</p>
          <p className="mb-0">Peça ao administrador do sistema para liberar o seu acesso em <strong>Usuários › Grupos da rede</strong>. Depois que ele gravar, entre de novo.</p>
        </Section>
      </Card></div></div>
    </>
  )
}

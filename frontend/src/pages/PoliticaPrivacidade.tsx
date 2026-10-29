export default function PoliticaPrivacidade() {
  return (
    <main className="container py-4" style={{ maxWidth: 820 }}>
      <h1 className="h3 mb-1">Política de Privacidade</h1>
      <p className="text-muted small">Gerenciador Patrimonial · Fundação Pró-Tocantins · Última atualização: 29/10/2026</p>

      <p>
        Esta política explica quais dados o aplicativo <strong>Gerenciador Patrimonial</strong> trata, para que finalidade,
        e como o usuário pode exercer seus direitos.
      </p>

      <h2 className="h5 mt-4">1. Controlador</h2>
      <p>
        Fundação Pró-Tocantins. Contato para assuntos de privacidade: <strong>admin@fasaudefpto.com.br</strong>.
      </p>

      <h2 className="h5 mt-4">2. Dados tratados</h2>
      <ul>
        <li>Dados de acesso: login, senha (armazenada apenas em forma criptografada) e perfil de uso.</li>
        <li>Dados funcionais de usuários, responsáveis e profissionais cadastrados: nome, lotação e demais informações necessárias à gestão.</li>
        <li>Dados de bens e frota: registros de patrimônio, levantamentos, veículos, cautelas, abastecimentos e sinistros.</li>
        <li>Fotos e anexos enviados pelo próprio usuário durante levantamentos e cadastros.</li>
        <li>Registros de auditoria: ações realizadas no sistema, com data, hora e usuário responsável.</li>
      </ul>

      <h2 className="h5 mt-4">3. Finalidade e base legal</h2>
      <p>
        Os dados são usados exclusivamente para gestão patrimonial e de frota da Fundação, controle de acesso e
        cumprimento de obrigações de controle interno. O tratamento se apoia no exercício de atividades institucionais
        e no legítimo interesse de controle administrativo, conforme a Lei nº 13.709/2018 (LGPD).
      </p>

      <h2 className="h5 mt-4">4. Compartilhamento</h2>
      <p>
        Não vendemos dados pessoais. Os dados não são compartilhados com terceiros, exceto quando exigido por lei ou
        ordem de autoridade competente.
      </p>

      <h2 className="h5 mt-4">5. Armazenamento e segurança</h2>
      <p>
        Os dados são armazenados em servidores da Fundação com controle de acesso por perfil. Cada usuário visualiza
        apenas as informações permitidas ao seu perfil. Adotamos medidas técnicas e administrativas para proteger os dados
        contra acesso não autorizado.
      </p>

      <h2 className="h5 mt-4">6. Uso offline</h2>
      <p>
        Em locais com sinal fraco, os registros podem ficar guardados no próprio aparelho e são enviados quando a conexão
        retorna. Esses dados locais são removidos após a sincronização.
      </p>

      <h2 className="h5 mt-4">7. Seus direitos</h2>
      <p>
        Você pode solicitar confirmação do tratamento, acesso, correção, anonimização ou eliminação de dados pessoais,
        pelo contato indicado no item 1.
      </p>

      <h2 className="h5 mt-4">8. Alterações</h2>
      <p>Esta política pode ser atualizada. A data da última atualização fica sempre indicada no topo desta página.</p>
    </main>
  )
}

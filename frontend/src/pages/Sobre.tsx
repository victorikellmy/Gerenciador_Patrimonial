import { Link } from 'react-router-dom'

export default function Sobre() {
  return (
    <main className="container py-4" style={{ maxWidth: 820 }}>
      <h1 className="h3 mb-1">Sobre o Gerenciador Patrimonial</h1>
      <p className="text-muted small">Fundação Pró-Tocantins</p>

      <p>
        O Gerenciador Patrimonial é uma ferramenta de gestão de bens, levantamentos, frota e diárias da
        <strong> Fundação Pró-Tocantins</strong>. O aplicativo é de uso restrito aos colaboradores da Fundação.
      </p>

      <div className="alert alert-warning" role="note">
        <strong>Aviso:</strong> este aplicativo <strong>não é uma entidade governamental</strong> e não representa,
        nem é oficial de, órgão público. Não somos afiliados à Polícia Militar do Tocantins (PMTO) nem ao Corpo de
        Bombeiros Militar do Tocantins (CBMTO), que são citados apenas como instituições apoiadas pela Fundação.
      </div>

      <h2 className="h5 mt-4">Fontes oficiais de informações governamentais</h2>
      <p>As informações de órgãos públicos exibidas ou referenciadas no aplicativo podem ser conferidas nas fontes oficiais:</p>
      <ul>
        <li>
          Portal da Transparência do Estado do Tocantins:{' '}
          <a href="https://transparencia.to.gov.br" target="_blank" rel="noopener noreferrer">transparencia.to.gov.br</a>
        </li>
        <li>
          Polícia Militar do Tocantins:{' '}
          <a href="https://www.pm.to.gov.br" target="_blank" rel="noopener noreferrer">pm.to.gov.br</a>
        </li>
        <li>
          Corpo de Bombeiros Militar do Tocantins:{' '}
          <a href="https://www.cbm.to.gov.br" target="_blank" rel="noopener noreferrer">cbm.to.gov.br</a>
        </li>
      </ul>

      <p>
        <Link to="/politica-de-privacidade">Política de Privacidade</Link>
      </p>
    </main>
  )
}

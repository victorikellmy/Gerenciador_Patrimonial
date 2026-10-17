import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { abrirArquivo } from '../api/http'
import { Card, PageHeader, Section } from '../components/ui'

export default function Relatorios() {
  const responsaveis = useQuery({ queryKey: ['responsaveis-todos'], queryFn: api.responsaveis.todos })
  const [resp, setResp] = useState('')
  return (
    <>
      <PageHeader titulo="Relatórios" subtitulo="Exportações patrimoniais em Excel, CSV e PDF." />
      <div className="row g-3">
        <div className="col-md-6 col-lg-4"><Card className="h-100"><Section icon="bi-file-earmark-spreadsheet" titulo="Inventário completo" desc="Todos os bens com lotação, responsável, valores e depreciação." semBorda>
          <div className="d-flex gap-2 flex-wrap"><button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.inventarioUrl('xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button><button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.inventarioUrl('csv'))}><i className="bi bi-filetype-csv"></i> CSV</button><button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.inventarioUrl('pdf'))}><i className="bi bi-file-earmark-pdf"></i> PDF</button></div>
        </Section></Card></div>
        <div className="col-md-6 col-lg-4"><Card className="h-100"><Section icon="bi-x-octagon" titulo="Baixas" desc="Bens baixados com data e motivo." semBorda>
          <div className="d-flex gap-2 flex-wrap"><button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.baixasUrl('xlsx'))}><i className="bi bi-file-earmark-excel"></i> Excel</button><button className="btn btn-secondary" onClick={() => abrirArquivo(api.relatorios.baixasUrl('csv'))}><i className="bi bi-filetype-csv"></i> CSV</button></div>
        </Section></Card></div>
        <div className="col-md-6 col-lg-4"><Card className="h-100"><Section icon="bi-file-earmark-pdf" titulo="Termo de responsabilidade" desc="PDF para assinatura com todos os bens ativos sob responsabilidade do servidor." semBorda>
          <div className="d-flex gap-2"><select className="form-select" value={resp} onChange={e => setResp(e.target.value)}><option value="">Selecione o responsável</option>{responsaveis.data?.filter(r => r.ativo).map(r => <option key={r.id} value={r.id}>{r.nomeCompleto}{r.lotacaoNome ? ` · ${r.lotacaoNome}` : ''}</option>)}</select><button className="btn btn-primary text-nowrap" disabled={!resp} onClick={() => abrirArquivo(api.relatorios.termoUrl(Number(resp)))}><i className="bi bi-file-earmark-pdf"></i> Gerar</button></div>
        </Section></Card></div>
      </div>
    </>
  )
}

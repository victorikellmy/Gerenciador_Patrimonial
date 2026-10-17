import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api'
import type { ImportResult } from '../api/types'
import { Card, PageHeader, Section, useToast } from '../components/ui'

export default function Importacao() {
  const qc = useQueryClient()
  const toast = useToast()
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ImportResult | null>(null)
  const enviar = async (e: FormEvent) => {
    e.preventDefault(); if (!arquivo) return; setEnviando(true); setResultado(null)
    try { const r = await api.importacao.importar(arquivo); setResultado(r); toast.sucesso(`${r.importados} bem(ns) importado(s).`); qc.invalidateQueries({ queryKey: ['patrimonios'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }); qc.invalidateQueries({ queryKey: ['lotacoes-todas'] }); qc.invalidateQueries({ queryKey: ['responsaveis-todos'] }) }
    catch (ex) { toast.erro(ex instanceof Error ? ex.message : 'Falha na importação.') } finally { setEnviando(false) }
  }
  return (
    <>
      <PageHeader titulo="Importação de patrimônios" subtitulo="Carga em lote a partir de planilha (CSV ou XLSX) no layout do inventário." />
      <div className="row g-3">
        <div className="col-lg-7">
          <Card><form onSubmit={enviar}>
            <Section icon="bi-upload" titulo="Arquivo" desc="Lotações e responsáveis inexistentes são criados automaticamente. Tombos já cadastrados são ignorados." semBorda>
              <input type="file" className="form-control" accept=".csv,.xlsx" required onChange={e => setArquivo(e.target.files?.[0] ?? null)} />
            </Section>
            <div className="sticky-actions"><button className="btn btn-primary" disabled={!arquivo || enviando}>{enviando ? <span className="spinner-border spinner-border-sm"></span> : <i className="bi bi-upload"></i>} Importar</button></div>
          </form></Card>
        </div>
        <div className="col-lg-5">
          {resultado && <Card><Section icon="bi-clipboard-data" titulo="Resultado" semBorda>
            <div className="row g-2 text-center mb-3">
              <div className="col-4"><div className="kpi-value">{resultado.total}</div><div className="kpi-label">linhas</div></div>
              <div className="col-4"><div className="kpi-value text-success">{resultado.importados}</div><div className="kpi-label">importados</div></div>
              <div className="col-4"><div className="kpi-value text-warning">{resultado.ignorados}</div><div className="kpi-label">ignorados</div></div>
            </div>
            <div className="small text-muted mb-2">{resultado.lotacoesCriadas} lotação(ões) e {resultado.responsaveisCriados} responsável(is) criados.</div>
            {resultado.erros.length > 0 && <div className="alert alert-warning small mb-0"><strong>Ocorrências:</strong><ul className="mb-0 mt-1">{resultado.erros.map((e, i) => <li key={i}>{e}</li>)}</ul></div>}
          </Section></Card>}
        </div>
      </div>
    </>
  )
}

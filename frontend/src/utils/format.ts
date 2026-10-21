export const moeda = (v?: number | null) => v === undefined || v === null ? '—' : 'R$ ' + Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const numero = (v?: number | null, casas = 0) => v === undefined || v === null ? '—' : Number(v).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas })
export const data = (iso?: string | null) => {
  if (!iso) return '—'
  const [y, m, d] = iso.substring(0, 10).split('-')
  return `${d}/${m}/${y}`
}
export const dataHora = (iso?: string | null) => iso ? data(iso) + ' ' + iso.substring(11, 16) : '—'
export const hora = (h?: string | null) => h ? h.substring(0, 5) : '—'
export const hojeIso = () => new Date().toISOString().substring(0, 10)
export const agoraHora = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

export const rotulos: Record<string, string> = {
  // combustíveis
  GASOLINA: 'Gasolina', ETANOL: 'Álcool / Etanol', DIESEL: 'Diesel', GNV: 'GNV',
  // serviços
  REVISAO: 'Revisão', CONSERTO: 'Conserto', TROCA_OLEO: 'Troca de óleo', BALANCEAMENTO: 'Balanceamento / alinhamento', PNEUS: 'Pneus', FREIOS: 'Freios', ELETRICA: 'Elétrica', LAVA_JATO: 'Lava-jato', OUTRO: 'Outro',
  // diárias
  SOLICITADO: 'Solicitada', CONFIRMADO: 'Confirmada', DEFERIDO: 'Deferida', INDEFERIDO: 'Indeferida', PAGO: 'Paga',
  VEICULO_OFICIAL: 'Veículo oficial', AEREO: 'Aéreo', RODOVIARIO: 'Rodoviário', PROPRIO: 'Próprio', CONVENIO: 'Convênio',
  // patrimônio
  ATIVO: 'Ativo', BAIXADO: 'Baixado', MANUTENCAO: 'Em manutenção', CAUTELADO: 'Cautelado', EM_APURACAO: 'Em apuração',
  NOVO: 'Novo', OTIMO: 'Ótimo', BOM: 'Bom', BOM_REGULAR: 'Bom/Regular', REGULAR: 'Regular', REGULAR_RUIM: 'Regular/Ruim', RUIM: 'Ruim', INSERVIVEL: 'Inservível',
  PENDENTE: 'Pendente', OK: 'OK', AVARIADO: 'Avariado', NAO_LOCALIZADO: 'Não localizado', CONCLUIDA: 'Concluída', DESCARTADA: 'Descartada',
  // perfis
  ADMINISTRADOR: 'Administrador', FISCAL: 'Fiscal', GERENTE: 'Gerente', PRESIDENTE: 'Presidente', FINANCEIRO: 'Financeiro', MOTORISTA: 'Motorista', INTEGRACAO: 'Integração',
  NOTA_FISCAL: 'Nota fiscal', FOTO: 'Foto', MANUAL: 'Manual',
  // seguro da frota
  VIGENTE: 'Vigente', VENCENDO: 'Vencendo', VENCIDO: 'Vencido', FUTURO: 'A iniciar',
  ABERTO: 'Aberto', EM_ANALISE: 'Em análise', INDENIZADO: 'Indenizado', NEGADO: 'Negado', ENCERRADO: 'Encerrado',
}
export const rotulo = (v?: string | null) => v ? (rotulos[v] ?? v) : '—'

export const badgeSituacaoSeguro: Record<string, string> = { VIGENTE: 'text-bg-success', VENCENDO: 'text-bg-warning', VENCIDO: 'text-bg-danger', FUTURO: 'text-bg-info' }
export const badgeSituacaoSinistro: Record<string, string> = { ABERTO: 'text-bg-warning', EM_ANALISE: 'text-bg-info', INDENIZADO: 'text-bg-success', NEGADO: 'text-bg-danger', ENCERRADO: 'text-bg-secondary' }
export const SITUACOES_SINISTRO = ['ABERTO', 'EM_ANALISE', 'INDENIZADO', 'NEGADO', 'ENCERRADO'] as const

export const badgeStatusDiaria: Record<string, string> = { SOLICITADO: 'text-bg-secondary', CONFIRMADO: 'text-bg-warning', DEFERIDO: 'text-bg-info', INDEFERIDO: 'text-bg-dark', PAGO: 'text-bg-success' }
export const CONSERVACOES = ['NOVO', 'OTIMO', 'BOM', 'BOM_REGULAR', 'REGULAR', 'REGULAR_RUIM', 'RUIM', 'INSERVIVEL'] as const
export const SITUACOES = ['ATIVO', 'BAIXADO', 'MANUTENCAO', 'CAUTELADO', 'EM_APURACAO'] as const
export const PERFIS = ['ADMINISTRADOR', 'FISCAL', 'GERENTE', 'PRESIDENTE', 'FINANCEIRO', 'MOTORISTA', 'INTEGRACAO'] as const

/** Prazo da apólice em texto curto ("vence em 12 dia(s)", "venceu há 3 dia(s)"...). */
export function prazoSeguro(s: { situacao: string; diasParaVencer: number; inicioVigencia: string }) {
  if (s.situacao === 'FUTURO') return `começa em ${data(s.inicioVigencia)}`
  if (s.diasParaVencer < 0) return `venceu há ${-s.diasParaVencer} dia(s)`
  if (s.diasParaVencer === 0) return 'vence hoje'
  return `vence em ${s.diasParaVencer} dia(s)`
}

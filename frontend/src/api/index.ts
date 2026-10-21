import { http, qs } from './http'
import type * as T from './types'

const pageParams = (page = 0, size = 20, sort?: string) => ({ page, size, sort })

export const api = {
  auth: {
    login: (login: string, senha: string) => http.post<T.Usuario>('/api/auth/login', { login, senha }),
    logout: () => http.post<void>('/api/auth/logout'),
    me: () => http.get<T.Usuario>('/api/me'),
    resumo: () => http.get<T.MeResumo>('/api/me/resumo'),
    trocarSenha: (senhaAtual: string, novaSenha: string, confirmacao: string) => http.post<void>('/api/me/senha', { senhaAtual, novaSenha, confirmacao }),
  },
  dashboard: () => http.get<T.DashboardMetrics>('/api/dashboard'),
  catalogos: {
    upms: () => http.get<string[]>('/api/catalogos/upms'),
    categorias: () => http.get<string[]>('/api/catalogos/categorias'),
    subcategorias: () => http.get<string[]>('/api/catalogos/subcategorias'),
  },
  patrimonios: {
    pesquisar: (filtro: Record<string, unknown>, page = 0, size = 20) => http.get<T.Page<T.Patrimonio>>('/api/patrimonios' + qs({ ...filtro, ...pageParams(page, size, 'id,desc') })),
    buscar: (id: number) => http.get<T.Patrimonio>(`/api/patrimonios/${id}`),
    criar: (r: T.PatrimonioRequest) => http.post<T.Patrimonio>('/api/patrimonios', r),
    atualizar: (id: number, r: T.PatrimonioRequest) => http.put<T.Patrimonio>(`/api/patrimonios/${id}`, r),
    movimentar: (id: number, novaLotacaoId: number | '', novoResponsavelId: number | '', observacao?: string) => http.post<T.Patrimonio>(`/api/patrimonios/${id}/movimentar`, { novaLotacaoId: novaLotacaoId || null, novoResponsavelId: novoResponsavelId || null, observacao }),
    baixar: (id: number, motivo: string) => http.post<T.Patrimonio>(`/api/patrimonios/${id}/baixa`, { motivo }),
    excluir: (id: number) => http.del<void>(`/api/patrimonios/${id}`),
    movimentacoes: (id: number) => http.get<T.Movimentacao[]>(`/api/patrimonios/${id}/movimentacoes`),
    anexos: (id: number) => http.get<T.Anexo[]>(`/api/patrimonios/${id}/anexos`),
    anexar: (id: number, arquivo: File, tipo: T.TipoAnexo) => { const fd = new FormData(); fd.append('arquivo', arquivo); fd.append('tipo', tipo); return http.post<T.Anexo>(`/api/patrimonios/${id}/anexos`, fd) },
    excluirAnexo: (anexoId: number) => http.del<void>(`/api/anexos/${anexoId}`),
  },
  lotacoes: {
    todas: () => http.get<T.Lotacao[]>('/api/lotacoes/todas'),
    listar: (page = 0, size = 20) => http.get<T.Page<T.Lotacao>>('/api/lotacoes' + qs(pageParams(page, size, 'upm,asc'))),
    criar: (r: Partial<T.Lotacao>) => http.post<T.Lotacao>('/api/lotacoes', r),
    atualizar: (id: number, r: Partial<T.Lotacao>) => http.put<T.Lotacao>(`/api/lotacoes/${id}`, r),
    trocarResponsavel: (id: number, respId: number) => http.put<T.Lotacao>(`/api/lotacoes/${id}/responsavel/${respId}`),
    excluir: (id: number) => http.del<void>(`/api/lotacoes/${id}`),
  },
  responsaveis: {
    todos: () => http.get<T.Responsavel[]>('/api/responsaveis/todos'),
    listar: (page = 0, size = 20) => http.get<T.Page<T.Responsavel>>('/api/responsaveis' + qs(pageParams(page, size, 'nomeCompleto,asc'))),
    criar: (r: Partial<T.Responsavel>) => http.post<T.Responsavel>('/api/responsaveis', r),
    atualizar: (id: number, r: Partial<T.Responsavel>) => http.put<T.Responsavel>(`/api/responsaveis/${id}`, r),
    inativar: (id: number) => http.del<void>(`/api/responsaveis/${id}`),
  },
  levantamentos: {
    listar: () => http.get<T.Levantamento[]>('/api/levantamentos'),
    abrir: (ano: number, descricao?: string, observacao?: string) => http.post<T.Levantamento>('/api/levantamentos', { ano, descricao, observacao }),
    buscar: (id: number) => http.get<T.Levantamento>(`/api/levantamentos/${id}`),
    guia: (id: number) => http.get<T.UpmGuia[]>(`/api/levantamentos/${id}/guia`),
    itensDoLocal: (id: number, lotacaoId: number) => http.get<T.ItemLevantamento[]>(`/api/levantamentos/${id}/locais/${lotacaoId}/itens`),
    ocorrencias: (id: number) => http.get<T.ItemLevantamento[]>(`/api/levantamentos/${id}/ocorrencias`),
    conferir: (id: number, itemId: number, dados: { resultado: T.ResultadoConferencia; conservacaoVerificada?: string; descricaoAvaria?: string; observacao?: string; lotacaoContextoId?: number }, foto?: File | null) => {
      const fd = new FormData()
      Object.entries(dados).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') fd.append(k, String(v)) })
      if (foto) fd.append('foto', foto)
      return http.post<T.ItemLevantamento>(`/api/levantamentos/${id}/itens/${itemId}/conferir`, fd)
    },
    desfazer: (id: number, itemId: number) => http.post<T.ItemLevantamento>(`/api/levantamentos/${id}/itens/${itemId}/desfazer`),
    localizar: (id: number, lotacaoId: number, tombo: string) => http.post<T.ItemLevantamento>(`/api/levantamentos/${id}/locais/${lotacaoId}/localizar`, { tombo }),
    movimentar: (id: number, itemId: number) => http.post<void>(`/api/levantamentos/${id}/itens/${itemId}/movimentar`),
    concluir: (id: number) => http.post<T.Levantamento>(`/api/levantamentos/${id}/concluir`),
    reabrir: (id: number) => http.post<T.Levantamento>(`/api/levantamentos/${id}/reabrir`),
  },
  pendencias: {
    listar: (status: T.StatusPendencia | '', page = 0, size = 20) => http.get<T.Page<T.Pendencia>>('/api/pendencias' + qs({ status, ...pageParams(page, size) })),
    contagem: () => http.get<Record<string, number>>('/api/pendencias/contagem'),
    buscar: (id: number) => http.get<T.Pendencia>(`/api/pendencias/${id}`),
    preparar: (id: number) => http.get<T.PatrimonioRequest>(`/api/pendencias/${id}/preparar`),
    patrimoniar: (id: number, r: T.PatrimonioRequest) => http.post<T.Patrimonio>(`/api/pendencias/${id}/patrimoniar`, r),
    concluir: (id: number) => http.post<void>(`/api/pendencias/${id}/concluir`),
    descartar: (id: number, motivo: string) => http.post<void>(`/api/pendencias/${id}/descartar`, { motivo }),
  },
  frota: {
    resumo: () => http.get<T.FrotaResumo>('/api/frota/resumo'),
    gestao: () => http.get<T.GestaoFrota>('/api/frota/gestao'),
    veiculos: (todos = false) => http.get<T.Veiculo[]>('/api/frota/veiculos' + qs({ todos })),
    veiculo: (id: number) => http.get<{ veiculo: T.Veiculo; resumo: T.VeiculoResumo }>(`/api/frota/veiculos/${id}`),
    historico: (id: number) => http.get<T.HistoricoItem[]>(`/api/frota/veiculos/${id}/historico`),
    criarVeiculo: (r: T.VeiculoRequest) => http.post<T.Veiculo>('/api/frota/veiculos', limparVazios(r)),
    atualizarVeiculo: (id: number, r: T.VeiculoRequest) => http.put<T.Veiculo>(`/api/frota/veiculos/${id}`, limparVazios(r)),
    manutencaoStatus: (id: number, emManutencao: boolean, motivo?: string) => http.post<T.Veiculo>(`/api/frota/veiculos/${id}/manutencao-status` + qs({ emManutencao, motivo })),
    ativo: (id: number, ativo: boolean) => http.post<T.Veiculo>(`/api/frota/veiculos/${id}/ativo` + qs({ ativo })),
    patrimoniosDisponiveis: () => http.get<T.Patrimonio[]>('/api/frota/patrimonios-disponiveis'),
    importacaoConfigurada: () => http.get<{ configurada: boolean }>('/api/frota/importacao-configurada'),
    importarTransportes: () => http.post<Record<string, number>>('/api/frota/importar-transportes'),
    catalogos: () => http.get<{ combustiveis: { valor: string; rotulo: string }[]; servicos: { valor: string; rotulo: string }[]; motivos: string[]; lotacoes: string[] }>('/api/frota/catalogos'),
    titulares: (termo: string) => http.get<string[]>('/api/frota/titulares' + qs({ termo })),
    abastecimentos: (f: Record<string, unknown>, page = 0, size = 25) => http.get<T.Page<T.Abastecimento>>('/api/frota/abastecimentos' + qs({ ...f, ...pageParams(page, size, 'data,desc') })),
    manutencoes: (f: Record<string, unknown>, page = 0, size = 25) => http.get<T.Page<T.Manutencao>>('/api/frota/manutencoes' + qs({ ...f, ...pageParams(page, size, 'data,desc') })),
    deslocamentos: (f: Record<string, unknown>, page = 0, size = 25) => http.get<T.Page<T.Deslocamento>>('/api/frota/deslocamentos' + qs({ ...f, ...pageParams(page, size, 'dataViagem,desc') })),
    // Com nota fiscal vai em multipart (parte "dados" JSON + "arquivo"); o back-end exige a NF do MOTORISTA.
    abastecer: (veiculoId: number, r: T.AbastecimentoRequest, nf?: File | null) => http.post<T.Abastecimento>(`/api/frota/veiculos/${veiculoId}/abastecimentos`, nf ? comArquivo(limparVazios(r), nf) : limparVazios(r)),
    atualizarAbastecimento: (id: number, r: T.AbastecimentoRequest) => http.put<T.Abastecimento>(`/api/frota/abastecimentos/${id}`, limparVazios(r)),
    excluirAbastecimento: (id: number) => http.del<void>(`/api/frota/abastecimentos/${id}`),
    manutencao: (veiculoId: number, r: T.ManutencaoRequest, nf?: File | null) => http.post<T.Manutencao>(`/api/frota/veiculos/${veiculoId}/manutencoes`, nf ? comArquivo(limparVazios(r), nf) : limparVazios(r)),
    atualizarManutencao: (id: number, r: T.ManutencaoRequest) => http.put<T.Manutencao>(`/api/frota/manutencoes/${id}`, limparVazios(r)),
    excluirManutencao: (id: number) => http.del<void>(`/api/frota/manutencoes/${id}`),
    deslocar: (veiculoId: number, r: T.DeslocamentoRequest) => http.post<T.Deslocamento>(`/api/frota/veiculos/${veiculoId}/deslocamentos`, limparVazios(r)),
    atualizarDeslocamento: (id: number, r: T.DeslocamentoRequest) => http.put<T.Deslocamento>(`/api/frota/deslocamentos/${id}`, limparVazios(r)),
    finalizarDeslocamento: (id: number, kmFinal: number, horaChegada?: string) => http.post<T.Deslocamento>(`/api/frota/deslocamentos/${id}/finalizar`, { kmFinal, horaChegada: horaChegada || null }),
    excluirDeslocamento: (id: number) => http.del<void>(`/api/frota/deslocamentos/${id}`),
    anexarNf: (tipo: 'abastecimentos' | 'manutencoes', id: number, arquivo: File) => { const fd = new FormData(); fd.append('arquivo', arquivo); return http.post<unknown>(`/api/frota/${tipo}/${id}/nota-fiscal`, fd) },
    nfUrl: (tipo: 'abastecimentos' | 'manutencoes', id: number) => `/api/frota/${tipo}/${id}/nota-fiscal`,
    exportUrl: (tipo: string, f: Record<string, unknown>, formato: 'xlsx' | 'pdf') => `/api/frota/export/${tipo}` + qs({ ...f, formato }),
    cautelas: (f: Record<string, unknown>, page = 0, size = 25) => http.get<T.Page<T.Cautela>>('/api/frota/cautelas' + qs({ ...f, ...pageParams(page, size, 'dataViagem,desc') })),
    cautela: (id: number) => http.get<T.Cautela>(`/api/frota/cautelas/${id}`),
    criarCautela: (r: T.CautelaRequest) => http.post<T.Cautela>('/api/frota/cautelas', limparVazios(r)),
    atualizarCautela: (id: number, r: T.CautelaRequest) => http.put<T.Cautela>(`/api/frota/cautelas/${id}`, limparVazios(r)),
    finalizarCautela: (id: number, kmFinal: number, horaChegada?: string) => http.post<T.Cautela>(`/api/frota/cautelas/${id}/finalizar`, { kmFinal, horaChegada: horaChegada || null }),
    excluirCautela: (id: number) => http.del<void>(`/api/frota/cautelas/${id}`),
    termoUrl: (id: number) => `/api/frota/cautelas/${id}/termo`,
    // Seguro: apólice (com arquivo opcional em multipart) e sinistros
    seguros: (f: { veiculoId?: number | string; situacao?: string; texto?: string } = {}) => http.get<T.Seguro[]>('/api/frota/seguros' + qs(f)),
    seguro: (id: number) => http.get<T.Seguro>(`/api/frota/seguros/${id}`),
    alertasSeguro: () => http.get<T.AlertasSeguro>('/api/frota/seguros/alertas'),
    criarSeguro: (r: T.SeguroRequest, apolice?: File | null) => http.post<T.Seguro>('/api/frota/seguros', apolice ? comArquivo(limparVazios(r), apolice) : limparVazios(r)),
    atualizarSeguro: (id: number, r: T.SeguroRequest) => http.put<T.Seguro>(`/api/frota/seguros/${id}`, limparVazios(r)),
    anexarApolice: (id: number, arquivo: File) => { const fd = new FormData(); fd.append('arquivo', arquivo); return http.post<T.Seguro>(`/api/frota/seguros/${id}/apolice`, fd) },
    apoliceUrl: (id: number) => `/api/frota/seguros/${id}/apolice`,
    excluirSeguro: (id: number) => http.del<void>(`/api/frota/seguros/${id}`),
    sinistros: (veiculoId?: number) => http.get<T.Sinistro[]>('/api/frota/sinistros' + qs({ veiculoId })),
    sinistro: (id: number) => http.get<T.Sinistro>(`/api/frota/sinistros/${id}`),
    criarSinistro: (r: T.SinistroRequest) => http.post<T.Sinistro>('/api/frota/sinistros', limparVazios(r)),
    atualizarSinistro: (id: number, r: T.SinistroRequest) => http.put<T.Sinistro>(`/api/frota/sinistros/${id}`, limparVazios(r)),
    excluirSinistro: (id: number) => http.del<void>(`/api/frota/sinistros/${id}`),
  },
  diarias: {
    listar: (f: Record<string, unknown>, page = 0, size = 25) => http.get<T.Page<T.Diaria>>('/api/diarias' + qs({ ...f, ...pageParams(page, size, 'dataSolicitacao,desc') })),
    contagens: () => http.get<Record<T.StatusDiaria, number>>('/api/diarias/contagens'),
    buscar: (id: number) => http.get<T.Diaria>(`/api/diarias/${id}`),
    historico: (id: number) => http.get<T.DiariaHistorico[]>(`/api/diarias/${id}/historico`),
    pdfUrl: (id: number) => `/api/diarias/${id}/pdf`,
    criar: (r: T.DiariaRequest) => http.post<T.Diaria>('/api/diarias', limparVazios(r)),
    atualizar: (id: number, r: T.DiariaRequest) => http.put<T.Diaria>(`/api/diarias/${id}`, limparVazios(r)),
    excluir: (id: number) => http.del<void>(`/api/diarias/${id}`),
    transicionar: (id: number, acao: 'confirmar' | 'deferir' | 'indeferir' | 'pagar', observacao?: string, dataPagamento?: string) => http.post<T.Diaria>(`/api/diarias/${id}/${acao}`, { observacao: observacao || null, dataPagamento: dataPagamento || null }),
    valores: (profissionalId: number) => http.get<T.Profissional>(`/api/diarias/valores/${profissionalId}`),
    pagas: (de?: string, ate?: string) => http.get<{ de: string; ate: string; lista: T.Diaria[]; total: number }>('/api/diarias/pagas' + qs({ de, ate })),
    pagasUrl: (de: string, ate: string, formato: 'xlsx' | 'pdf') => '/api/diarias/pagas/download' + qs({ de, ate, formato }),
    profissionais: (busca?: string, ativos = false) => http.get<T.Profissional[]>('/api/diarias/profissionais' + qs({ busca, ativos })),
    profissional: (id: number) => http.get<T.Profissional>(`/api/diarias/profissionais/${id}`),
    criarProfissional: (r: T.ProfissionalRequest) => http.post<T.Profissional>('/api/diarias/profissionais', r),
    atualizarProfissional: (id: number, r: T.ProfissionalRequest) => http.put<T.Profissional>(`/api/diarias/profissionais/${id}`, r),
    ativoProfissional: (id: number, ativo: boolean) => http.post<T.Profissional>(`/api/diarias/profissionais/${id}/ativo` + qs({ ativo })),
    niveis: (ativos = false) => http.get<T.NivelDiaria[]>('/api/diarias/niveis' + qs({ ativos })),
    salvarNivel: (id: number | null, r: Omit<T.NivelDiaria, 'id' | 'ativo'>) => id ? http.post<T.NivelDiaria>(`/api/diarias/niveis/${id}`, r) : http.post<T.NivelDiaria>('/api/diarias/niveis', r),
    ativoNivel: (id: number, ativo: boolean) => http.post<T.NivelDiaria[]>(`/api/diarias/niveis/${id}/ativo` + qs({ ativo })),
  },
  usuarios: {
    listar: (page = 0, size = 50) => http.get<T.Page<T.Usuario>>('/api/usuarios' + qs(pageParams(page, size, 'nomeCompleto,asc'))),
    buscar: (id: number) => http.get<T.Usuario>(`/api/usuarios/${id}`),
    criar: (r: T.UsuarioRequest) => http.post<T.Usuario>('/api/usuarios', r),
    atualizar: (id: number, r: T.UsuarioRequest) => http.put<T.Usuario>(`/api/usuarios/${id}`, r),
    inativar: (id: number) => http.del<void>(`/api/usuarios/${id}`),
  },
  auditoria: {
    buscar: (f: Record<string, unknown>, page = 0, size = 50) => http.get<T.Page<T.Auditoria>>('/api/admin/auditoria' + qs({ ...f, page, size })),
  },
  importacao: {
    importar: (arquivo: File) => { const fd = new FormData(); fd.append('arquivo', arquivo); return http.post<T.ImportResult>('/api/importacao/patrimonios', fd) },
  },
  relatorios: {
    inventarioUrl: (formato: string) => `/api/relatorios/inventario?formato=${formato}`,
    baixasUrl: (formato: string) => `/api/relatorios/baixas?formato=${formato}`,
    termoUrl: (responsavelId: number) => `/api/relatorios/termo-responsabilidade/${responsavelId}`,
    levantamentoUrl: (id: number, formato: string) => `/api/levantamentos/${id}/download?formato=${formato}`,
  },
}

/** Monta multipart com os dados em JSON (parte "dados") e o arquivo (parte "arquivo"). */
function comArquivo(dados: object, arquivo: File): FormData {
  const fd = new FormData()
  fd.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }))
  fd.append('arquivo', arquivo)
  return fd
}

/** Converte '' em null para o Jackson/Bean Validation do back-end. */
function limparVazios<T extends object>(obj: T): T {
  const out: Record<string, unknown> = {}
  Object.entries(obj).forEach(([k, v]) => { out[k] = v === '' ? null : v })
  return out as T
}

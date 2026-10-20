// Espelho dos DTOs do back-end (dto/response e dto/request).

export type Perfil = 'ADMINISTRADOR' | 'FISCAL' | 'GERENTE' | 'PRESIDENTE' | 'FINANCEIRO' | 'MOTORISTA' | 'INTEGRACAO'
export type Conservacao = 'NOVO' | 'OTIMO' | 'BOM' | 'BOM_REGULAR' | 'REGULAR' | 'REGULAR_RUIM' | 'RUIM' | 'INSERVIVEL'
export type SituacaoPatrimonio = 'ATIVO' | 'BAIXADO' | 'MANUTENCAO' | 'CAUTELADO' | 'EM_APURACAO'
export type TipoAnexo = 'NOTA_FISCAL' | 'FOTO' | 'MANUAL' | 'OUTRO'
export type ResultadoConferencia = 'PENDENTE' | 'OK' | 'AVARIADO' | 'NAO_LOCALIZADO'
export type StatusPendencia = 'PENDENTE' | 'CONCLUIDA' | 'DESCARTADA'
export type Combustivel = 'GASOLINA' | 'ETANOL' | 'DIESEL' | 'GNV'
export type TipoServicoVeiculo = 'REVISAO' | 'CONSERTO' | 'TROCA_OLEO' | 'BALANCEAMENTO' | 'PNEUS' | 'FREIOS' | 'ELETRICA' | 'LAVA_JATO' | 'OUTRO'
export type StatusDiaria = 'SOLICITADO' | 'CONFIRMADO' | 'DEFERIDO' | 'INDEFERIDO' | 'PAGO'
export type MeioTransporte = 'VEICULO_OFICIAL' | 'AEREO' | 'RODOVIARIO'
export type RecursoDiaria = 'PROPRIO' | 'CONVENIO'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface Usuario {
  id: number; nomeCompleto: string; login: string; perfil: Perfil; ativo: boolean; senhaProvisoria: boolean; podeAbastecer?: boolean; criadoEm?: string
}
export interface UsuarioRequest {
  nomeCompleto: string; login: string; senha?: string; perfil: Perfil; ativo: boolean; exigirTrocaSenha: boolean; podeAbastecer?: boolean
}

export interface Lotacao { id: number; upm: string; nome: string; cidade?: string; tipoLocal: string; responsavelAtualId?: number; responsavelAtualNome?: string }
export interface Responsavel { id: number; nomeCompleto: string; matricula?: string; cidade?: string; lotacaoId?: number; lotacaoNome?: string; ativo: boolean }

export interface Patrimonio {
  id: number; numeroTombo?: string; descricao: string; categoria?: string; subcategoria?: string
  dataCompra?: string; valorCompra?: number; conservacao?: Conservacao; situacao: SituacaoPatrimonio; notaFiscal?: string
  lotacaoId?: number; lotacaoNome?: string; lotacaoUpm?: string; responsavelId?: number; responsavelNome?: string
  dataBaixa?: string; motivoBaixa?: string
  vutAnos?: number; percentualVud?: number; vudAnos?: number; vurAnos?: number; depreciacaoAcumulada?: number
  valorContabilLiquido?: number; valorRecuperavel?: number; perdaImpairment?: number; depreciacaoAnual?: number
  dataReferencia?: string; calculoLegado?: boolean; conclusaoImpairment?: string; observacao?: string; linkReferencia?: string
  criadoEm?: string; atualizadoEm?: string; criadoPor?: string; atualizadoPor?: string
}
export interface PatrimonioRequest {
  numeroTombo?: string; descricao: string; categoria?: string; subcategoria?: string; dataCompra?: string; valorCompra?: number | null
  conservacao?: Conservacao | ''; notaFiscal?: string; valorRecuperavel?: number | null; conclusaoImpairment?: string
  observacao?: string; linkReferencia?: string; lotacaoId?: number | ''; responsavelId?: number | ''
}
export interface Movimentacao {
  id: number; patrimonioId: number; lotacaoOrigemNome?: string; lotacaoOrigemUpm?: string; lotacaoDestinoNome?: string
  lotacaoDestinoUpm?: string; responsavelOrigemNome?: string; responsavelDestinoNome?: string; dataMovimentacao: string
  observacao?: string; executadoPor?: string
}
export interface Anexo { id: number; nomeOriginal: string; tipo: TipoAnexo; contentType?: string; tamanhoBytes?: number; criadoEm?: string }

export interface Agrupamento { chave: string; quantidade: number; valorTotal?: number }
export interface DashboardMetrics {
  totalPatrimonios: number; contagemPorSituacao: Record<string, number>; totalLotacoes: number; totalResponsaveis: number
  valorTotalAtivos: number; depreciacaoAcumuladaTotal: number; valorContabilLiquidoTotal: number
  porCategoria: Agrupamento[]; porConservacao: Agrupamento[]; topUpms: Agrupamento[]
  ultimasMovimentacoes: { id: number; patrimonioDescricao?: string; patrimonioTombo?: string; deLotacao?: string; paraLotacao?: string; deResponsavel?: string; paraResponsavel?: string; dataMovimentacao?: string }[]
  pendenciasPatrimoniamento: number
}

export interface Levantamento {
  id: number; ano: number; descricao: string; status: 'ABERTO' | 'CONCLUIDO'; observacao?: string; abertoPor?: string; abertoEm?: string
  concluidoPor?: string; concluidoEm?: string; total: number; pendentes: number; ok: number; avariados: number; naoLocalizados: number; divergenciasLocal: number
}
export interface LocalLevantamento { lotacaoId: number; upm: string; nome: string; total: number; pendentes: number; ok: number; avariados: number; naoLocalizados: number; encontradosAqui: number }
export interface UpmGuia { upm: string; locais: LocalLevantamento[] }
export interface ItemLevantamento {
  id: number; patrimonioId: number; numeroTombo?: string; descricao: string; categoria?: string; subcategoria?: string
  situacao: SituacaoPatrimonio; conservacaoAtual?: Conservacao; responsavelNome?: string
  lotacaoId: number; lotacaoUpm: string; lotacaoNome: string; lotacaoEncontradaId?: number; lotacaoEncontradaUpm?: string; lotacaoEncontradaNome?: string
  resultado: ResultadoConferencia; conservacaoAnterior?: Conservacao; conservacaoVerificada?: Conservacao
  descricaoAvaria?: string; observacao?: string; fotoId?: number; fotoAtualId?: number; verificadoPor?: string; verificadoEm?: string
}

export interface Pendencia {
  id: number; origem: string; compraIdOrigem: number; status: StatusPendencia; numeroDocumento?: string; numeroSgd?: string; assunto?: string
  solicitanteDocumento?: string; fornecedor?: string; numeroNotaFiscal?: string; dataRecebimento?: string; dataCompra?: string; valorTotal?: number
  retiradoPor?: string; setorDestino?: string; setorDestinoCentroCusto?: string; registradoPor?: string; observacao?: string
  itens: { descricao: string; codigoSku?: string; quantidade?: number; valorUnitario?: number }[]
  quantidadeTotal: number; quantidadePatrimoniada: number; quantidadeRestante: number; possuiNotaFiscal: boolean; nfNomeOriginal?: string
  patrimonioId?: number; concluidaPor?: string; concluidaEm?: string; criadoEm?: string
}

// --- Frota ---
export interface Veiculo {
  id: number; patrimonioId: number; numeroTombo?: string; placa: string; modelo: string; marca?: string; chassi?: string; renavam?: string
  ano?: string; cidade?: string; combustivelPadrao?: Combustivel; kmAtual?: number; ativo: boolean; rotulo: string
  situacao: SituacaoPatrimonio; conservacao?: Conservacao; dataCompra?: string; valorCompra?: number; notaFiscal?: string; observacao?: string
  lotacaoId?: number; lotacaoUpm?: string; lotacaoNome?: string; responsavelId?: number; responsavelNome?: string; naRua: boolean; ultimaSaida?: string
}
export interface VeiculoRequest {
  patrimonioId?: number | ''; placa: string; modelo: string; marca?: string; chassi?: string; renavam?: string; ano?: string; cidade?: string
  combustivelPadrao?: Combustivel | ''; kmAtual?: number | ''; numeroTombo?: string; dataCompra?: string; valorCompra?: number | ''
  conservacao?: Conservacao | ''; notaFiscal?: string; observacao?: string; lotacaoId?: number | ''; responsavelId?: number | ''
}
export interface Abastecimento {
  id: number; veiculoId: number; placa: string; modelo: string; data: string; km?: number; combustivel: Combustivel; quantidadeLitros?: number
  valor: number; precoLitro?: number; postoCidade?: string; motorista: string; observacao?: string; registradoPor?: string; possuiNotaFiscal: boolean; nfNomeOriginal?: string
}
export interface AbastecimentoRequest { veiculoId: number | ''; data: string; km?: number | ''; combustivel: Combustivel | ''; quantidadeLitros?: number | ''; valor: number | ''; postoCidade?: string; motorista: string; observacao?: string }
export interface Manutencao {
  id: number; veiculoId: number; placa: string; modelo: string; data: string; servico: TipoServicoVeiculo; km?: number; descricao?: string; valor: number
  fornecedor?: string; notaFiscal?: string; proximaRevisaoKm?: number; proximaRevisaoData?: string; motorista: string; registradoPor?: string; possuiNotaFiscal: boolean; nfNomeOriginal?: string
}
export interface ManutencaoRequest { veiculoId: number | ''; data: string; servico: TipoServicoVeiculo | ''; km?: number | ''; descricao?: string; valor?: number | ''; fornecedor?: string; notaFiscal?: string; proximaRevisaoKm?: number | ''; proximaRevisaoData?: string; motorista: string }
export interface Deslocamento {
  id: number; veiculoId: number; placa: string; modelo: string; dataViagem: string; destino: string; motivo: string; titular?: string; assistido?: string
  horaSaida?: string; kmInicial?: number; horaChegada?: string; kmFinal?: number; kmRodados?: number; emAndamento: boolean; motorista: string; lotacao?: string; observacao?: string; registradoPor?: string
}
export interface DeslocamentoRequest { veiculoId: number | ''; dataViagem: string; destino: string; motivo: string; titular?: string; assistido?: string; horaSaida?: string; kmInicial?: number | ''; horaChegada?: string; kmFinal?: number | ''; motorista: string; lotacao?: string; observacao?: string }
export interface HistoricoItem { tipo: 'ABASTECIMENTO' | 'MANUTENCAO' | 'DESLOCAMENTO'; id: number; data: string; hora?: string; titulo: string; detalhe?: string; km?: number; valor?: number; motorista?: string; emAndamento: boolean; possuiNotaFiscal: boolean }
export interface VeiculoResumo {
  kmAtual?: number; combustivelAno: number; litrosAno: number; manutencaoAno: number; kmRodadosAno: number; consumoMedioKmPorLitro?: number
  qtdAbastecimentos: number; qtdManutencoes: number; qtdDeslocamentos: number; deslocamentoAberto?: Deslocamento; proximaRevisaoKm?: number; proximaRevisaoData?: string; revisaoVencida: boolean
}
export interface FrotaResumo { veiculosAtivos: number; naRua: number; deslocamentosMes: number; combustivelMes: number; manutencaoMes: number; manutencoesMes: number; emAndamento: Deslocamento[] }
export interface GestaoFrota {
  hoje: string; veiculosAtivos: number; naRua: number; emManutencao: number; disponiveis: number; revisoesVencidas: number
  combustivelMes: number; manutencaoMes: number; combustivelAno: number; manutencaoAno: number; kmMes: number; kmAno: number; custoMedioPorKmAno?: number
  situacoes: { veiculoId: number; placa: string; modelo: string; cidade?: string; estado: 'NA_RUA' | 'MANUTENCAO' | 'DISPONIVEL' | 'INATIVO'; motorista?: string; destino?: string; desde?: string; horaSaida?: string; kmAtual?: number; ultimaSaida?: string; ultimaManutencao?: string; dataUltimaManutencao?: string; proximaRevisaoKm?: number; proximaRevisaoData?: string; revisaoVencida: boolean }[]
  gastosPorVeiculo: { veiculoId: number; placa: string; modelo: string; combustivel: number; litros: number; manutencao: number; manutencoes: number; total: number; kmRodados: number; saidas: number; custoPorKm?: number }[]
  gastosPorMes: { ano: number; mes: number; rotulo: string; combustivel: number; manutencao: number }[]
  alertasRevisao: GestaoFrota['situacoes']
}
export interface Cautela {
  id: number; veiculoId: number; placa: string; modelo: string; dataViagem: string; destino: string; motivo: string; responsavel?: string; assistido?: string
  horaSaida?: string; kmInicial?: number; horaChegada?: string; kmFinal?: number; kmRodados?: number; emAndamento: boolean; motoristaNome: string; rg?: string; cpf?: string; fone?: string; lotacao?: string; observacao?: string
}
export interface CautelaRequest { veiculoId: number | ''; dataViagem: string; destino: string; motivo: string; responsavel?: string; assistido?: string; horaSaida?: string; kmInicial?: number | ''; horaChegada?: string; kmFinal?: number | ''; motoristaNome: string; rg: string; cpf: string; fone: string; lotacao: string; observacao?: string }

// --- Diárias ---
export interface NivelDiaria { id: number; descricao: string; valorCapitais: number; valorInteriorEstado: number; valorInteriorOutroEstado: number; ativo: boolean }
export interface Profissional { id: number; nome: string; matricula?: string; lotacao?: string; nivelId: number; nivelDescricao: string; valorCapitais: number; valorInteriorEstado: number; valorInteriorOutroEstado: number; cpf: string; rg?: string; orgaoExpedidor?: string; dadosBancarios?: string; ativo: boolean; cadastradoPor?: string }
export interface ProfissionalRequest { nome: string; matricula?: string; lotacao?: string; nivelId: number | ''; cpf: string; rg?: string; orgaoExpedidor?: string; dadosBancarios?: string }
export interface Diaria {
  id: number; dataSolicitacao: string; profissionalId: number; profissionalNome: string; profissionalMatricula?: string; profissionalLotacao?: string; profissionalNivel?: string
  profissionalCpf?: string; profissionalRg?: string; profissionalOrgaoExpedidor?: string; profissionalDadosBancarios?: string
  itinerario: string; horaSaida?: string; meioTransporte: MeioTransporte; partida: string; retorno: string; horaChegada?: string; recurso: RecursoDiaria
  numeroDiarias: number; valorUnitario: number; valorTotal: number; finalidade?: string; solicitante: string; status: StatusDiaria; dataPagamento?: string; motivoIndeferimento?: string; criadoEm?: string; editavel: boolean
}
export interface DiariaRequest { profissionalId: number | ''; itinerario: string; partida: string; horaSaida?: string; retorno: string; horaChegada?: string; meioTransporte: MeioTransporte; recurso: RecursoDiaria; numeroDiarias: number | ''; valorUnitario: number | ''; finalidade?: string }
export interface DiariaHistorico { id: number; deStatus?: StatusDiaria; paraStatus: StatusDiaria; usuario: string; instante: string; observacao?: string }

export interface Auditoria { id: number; usuario: string; acao: string; entidade: string; entidadeId?: number; descricao?: string; ipOrigem?: string; dataHora: string }
export interface ImportResult { total: number; importados: number; ignorados: number; erros: string[]; lotacoesCriadas: number; responsaveisCriados: number }
export interface MeResumo { pendenciasPatrimoniamento: number; levantamentoAberto?: Levantamento | null; diariasPendentes: number }

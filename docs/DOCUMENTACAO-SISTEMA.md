# Gerenciador Patrimonial — Documentação Técnica

Sistema de gestão de bens patrimoniais da Fundação (Fasaúde), cobrindo o ciclo de vida completo do patrimônio: cadastro, movimentação, depreciação contábil, baixa, anexos, relatórios oficiais, importação em lote e trilha de auditoria.

> Documento correspondente ao estado do código no commit `095ed1f` (branch `master`).

---

## 1. Visão geral

O sistema substitui o controle patrimonial em planilha Excel: cada bem tem número de tombo, lotação (UPM + sala), responsável, estado de conservação e valor contábil calculado automaticamente. Duas interfaces sobre o mesmo backend:

- **Interface web (Thymeleaf)** — operação diária: dashboard, cadastro, pesquisa, movimentação, baixa, relatórios, importação com acompanhamento de progresso e auditoria.
- **API REST (`/api/**`)** — os mesmos casos de uso para integração via scripts/sistemas externos, com HTTP Basic stateless.

---

## 2. Tecnologias

| Camada | Tecnologia | Versão / Observação |
|---|---|---|
| Linguagem | Java | 21 (toolchain Gradle) |
| Framework | Spring Boot | 3.3.5 |
| Build | Gradle | wrapper incluído (`gradlew`) |
| Persistência | Spring Data JPA / Hibernate 6 | `open-in-view: false`, batch JDBC, `default_batch_fetch_size: 16` |
| Banco (dev) | H2 em memória | `MODE=PostgreSQL`, console em `/h2-console` |
| Banco (prod) | PostgreSQL | perfil `prod` |
| Migrations | Flyway 10.20.1 | `V1`–`V5` comuns + `V6` específica de PostgreSQL (`db/vendor/postgresql`) |
| Segurança | Spring Security 6 | 2 filter chains (web com sessão + API stateless) |
| Frontend | Thymeleaf + Bootstrap + Bootstrap Icons | fragmentos reutilizáveis (`layout`, `ui-kit`) |
| Excel | Apache POI 5.3.0 | leitura via arquivo temporário; escrita com SXSSF (streaming) |
| PDF | OpenPDF 1.3.34 | tabela incremental (flush a cada 200 linhas) |
| Cache | Caffeine + Spring Cache | TTL 60 s (dashboard, dropdowns, UserDetails) |
| Assíncrono | Spring `@Async` | auditoria e jobs de importação |
| Boilerplate | Lombok | `@Getter/@Builder/@RequiredArgsConstructor` |
| Testes | JUnit 5, Mockito, AssertJ, `@DataJpaTest` | 43 testes |

---

## 3. Arquitetura

Monólito em camadas:

```
web/ (Thymeleaf)  controller/ (REST)
        │               │
        └───► service/ ◄┘      regras de negócio, transações, cache, jobs
                │
          repository/           Spring Data JPA + Specifications + @EntityGraph
                │
          domain/entity          agregados JPA (Patrimonio é a raiz)
```

### Pacotes principais

```
├── config/          JpaAuditingConfig, CacheConfig (Caffeine), AsyncConfig (@EnableAsync)
├── domain/
│   ├── entity/      Patrimonio, Lotacao, Responsavel, Movimentacao, Usuario,
│   │                ArquivoAnexo, AuditoriaAcao, VidaUtilCategoria, PercentualConservacao
│   ├── enums/       SituacaoPatrimonio, Conservacao, Perfil, TipoLocal, TipoAnexo, AcaoAuditoria
│   ├── projection/  PatrimonioDepreciavel (5 colunas p/ depreciação em massa)
│   ├── diff/        DiffPatrimonio (descreve mudanças p/ auditoria)
│   └── catalog/     SubcategoriaCatalog
├── dto/request|response/   payloads validados; entidade JPA nunca sai para view/JSON
├── repository/      Spring Data JPA; spec/ (PatrimonioSpecifications, AuditoriaSpecifications)
├── service/
│   ├── PatrimonioService, LotacaoService, ResponsavelService, UsuarioService, AnexoService
│   ├── DepreciacaoService              (2 estratégias de cálculo; mapa único de VUT)
│   ├── AuditoriaService + AuditoriaGravador (gravação assíncrona)
│   ├── AuditoriaConsultaService        (busca compartilhada web × REST)
│   ├── dashboard/DashboardService      (agregações SQL + cache)
│   ├── importer/  ExcelImportService, ImportJobService, ImportJobRunner,
│   │              ProgressoImportacao, CellReader, Normalizadores, ImportBootstrapRunner
│   ├── report/    RelatorioService + exporter/ (Csv, Xlsx, Pdf, LinhaInventario)
│   └── storage/   StorageService → LocalFileStorageService (uploads em disco)
├── web/             controllers Thymeleaf (inclui tela de status da importação)
├── controller/      controllers REST (/api/**)
├── security/        SecurityConfig, CustomUserDetailsService, SecurityUtils,
│                    AuditorAwareImpl, AdminBootstrapRunner, AuditoriaLoginListener
├── exception/       GlobalExceptionHandler (REST) + WebExceptionHandler (telas)
└── util/            Textos (nullIfBlank/truncar), Paginacao (clamp de page size)
```

### Princípios adotados

- **Invariantes na entidade** (ex.: `Patrimonio.movimentar()` rejeita bem baixado); **orquestração no service** (tombo único, auditoria, transações).
- **Campos derivados não são persistidos**: depreciação, VCL e impairment são calculados sob demanda pelo `DepreciacaoService`.
- **Fonte única** para lógica repetida: `SecurityUtils.usuarioAtualOuSystem()`, `Textos`, `Paginacao`, `LinhaInventario` (modelo comum dos 3 exporters), mapa de VUT no `DepreciacaoService`.

### Modelo de dados (principais entidades)

- **Patrimonio** (raiz): tombo (único, opcional), descrição, categoria/subcategoria, data/valor de compra, conservação, situação, nota fiscal, campos de impairment, lotação, responsável, anexos, histórico de movimentações, colunas de auditoria.
- **Lotacao**: chave de negócio `(upm, nome)` única; tipo INTERNO/EXTERNO; responsável atual do setor.
- **Responsavel**: nome, matrícula (única quando informada), lotação, soft delete via `ativo`.
- **Movimentacao**: trilha imutável de trocas de lotação/responsável (origem → destino, quem, quando).
- **Usuario**: login único, hash BCrypt, perfil (RBAC), `ativo`.
- **AuditoriaAcao**: trilha append-only (usuário, ação, entidade, diff, IP).
- **VidaUtilCategoria** / **PercentualConservacao**: tabelas de referência (seed Flyway) do cálculo de depreciação.

---

## 4. Segurança

### Autenticação
- **Web**: form login com sessão (`/login`), logout com invalidação de cookie, CSRF **habilitado** em toda a interface web.
- **API (`/api/**`)**: HTTP Basic, **stateless**, CSRF desabilitado apenas nesse escopo. O lookup do usuário usa **cache de `UserDetails`** (Caffeine, TTL 60 s) no `DaoAuthenticationProvider` — sem SELECT por request; o cache é invalidado na hora quando o usuário é alterado, inativado ou troca de senha. O BCrypt continua verificado a cada request (custo inerente do esquema Basic).
- Senhas armazenadas exclusivamente como **hash BCrypt (cost 10)**.
- Login/logout registrados na trilha de auditoria (`AuditoriaLoginListener`).

### Autorização (RBAC)

| Ação | ADMINISTRADOR | FISCAL |
|---|---|---|
| Consultar, pesquisar, relatórios | ✔ | ✔ |
| Cadastrar/editar patrimônio, movimentar | ✔ | ✔ |
| Dar baixa, excluir definitivamente | ✔ | ✖ |
| Excluir lotação, inativar responsável | ✔ | ✖ |
| Gestão de usuários (`/usuarios`) | ✔ | ✖ |
| Importação — telas e API, incluindo status de jobs (`/importacao`, `/api/importacao/**`) | ✔ | ✖ |
| Trilha de auditoria (`/admin/auditoria`) | ✔ | ✖ |

Implementado em duas camadas: regras por rota no `SecurityConfig` + `@PreAuthorize` nos controllers sensíveis.

### Proteções adicionais
- **Bootstrap seguro do admin**: cria `admin` no primeiro start apenas se não existir nenhum ADMINISTRADOR ativo; senha via `app.admin.senha` (em produção, variável de ambiente/secret) com aviso de troca imediata.
- **Situação não é editável via PUT comum** — só pelos endpoints de baixa/movimentação.
- **Soft delete** de usuários e responsáveis preserva rastreabilidade.
- **Trilha de auditoria dupla**: colunas `@CreatedBy/@LastModifiedBy` em cada registro + tabela `auditoria_acao` com diff campo a campo e IP (suporte a `X-Forwarded-For`). A gravação é **assíncrona**: usuário/IP são capturados no thread da request e o INSERT roda no executor — falha de auditoria não derruba a operação de negócio, e a operação não segura duas conexões.
- Uploads limitados (`max-file-size: 10MB`, request 25MB); anexos em **filesystem**, servidos com streaming.

---

## 5. Funcionalidades implementadas

### 5.1 Gestão de patrimônio
- CRUD completo com Bean Validation nos DTOs; **tombo único** validado na criação e edição.
- **Pesquisa dinâmica** paginada com filtros combináveis (descrição, tombo, UPM, categoria, situação, conservação) via JPA Specifications.
- **Movimentação**: troca de lotação e/ou responsável com registro histórico imutável; bem baixado não pode ser movimentado; exige ao menos um destino.
- **Baixa lógica**: situação BAIXADO + data + motivo obrigatório; baixa dupla rejeitada.
- **Exclusão física** apenas para erro de cadastro (ADMINISTRADOR), auditada.
- **Anexos** por patrimônio armazenados em disco, download via streaming.
- Histórico de movimentações por bem (mais recente primeiro, `LIMIT 1` para a última).

### 5.2 Depreciação e impairment (`DepreciacaoService`)
Duas estratégias, decididas pelo dado disponível:

- **TEMPO** (preferida, quando há `dataCompra`): depreciação linear — `depreciacaoAnual = valor / VUT`; VUD = dias corridos / 365,25, com **caps** (nunca além da VUT nem do custo); data futura não deprecia.
- **CONSERVAÇÃO** (legado, sem `dataCompra`): % de vida útil decorrida por estado de conservação — preserva as métricas dos registros históricos importados.

Saída única (`CalculoDepreciacao`): VUT, %VUD, VUD/VUR, depreciação acumulada, VCL, depreciação anual e **perda por impairment** = max(0, VCL − valor recuperável). Tabelas de referência pré-carregadas em memória no startup; o mapa de VUT é exposto também ao importador (fonte única).

### 5.3 Importação de planilha Excel
- Importa a "Planilha de Reconstituição de Dados" (22 colunas mapeadas), com seed automático idempotente no primeiro start (dev).
- **Execução assíncrona com status**: o upload dispara um job em background (UUID); a tela de status mostra barra de progresso com auto-refresh e o resultado final (KPIs + erros por linha). Na API: `POST /api/importacao/patrimonios/async` → **202** com `jobId`; `GET /api/importacao/jobs/{id}` → estado/progresso/resultado. O endpoint síncrono `POST /api/importacao/patrimonios` permanece para compatibilidade. Jobs são retidos por 2 h em memória.
- **Normalização de dados sujos**: UPM ("1BPM" → "1 BPM"), células corrompidas por fórmula, typos conhecidos, tombos placeholder ("0", "-") → null. Regex pré-compiladas.
- **Deduplicação** de tombo contra o banco e dentro da planilha, com pré-carga em memória (3 queries no total — **nenhum SELECT por linha**).
- **Upsert** de lotações e responsáveis inexistentes durante a carga.
- **Resiliência**: transação por chunk de 100 linhas; chunk que falha é reprocessado linha a linha (caches com par global/delta revertem junto com o rollback) — uma linha ruim vira erro descritivo sem abortar o restante.
- Leitura via arquivo temporário (POI com acesso randômico) — o .xlsx não é carregado inteiro em RAM.

### 5.4 Relatórios
- **Inventário completo**: tela paginada com VCL pré-calculado por item + downloads **CSV** (UTF-8 com BOM, `;`, pt-BR), **XLSX** (streaming SXSSF) e **PDF** (paisagem, tabela incremental). As 18 colunas vêm de fonte única (`LinhaInventario`), com a depreciação calculada 1x por item.
- **Relatório de baixas** (tela + CSV/XLSX).
- **Termo de responsabilidade** em PDF por responsável: identificação, texto legal, tabela de bens ativos com total e assinaturas.
- Exports não seguram conexão JDBC durante a escrita no response (consulta em transação curta, streaming fora dela).

### 5.5 Dashboard
Métricas agregadas em SQL com constructor expressions (sem hidratar entidades nem casts manuais): total por situação, valor total dos ativos, depreciação acumulada e VCL totais, agrupamentos por categoria/conservação/top-10 UPMs, últimas 10 movimentações. Cacheado por 60 s.

### 5.6 Administração
- **Usuários**: CRUD com inativação, troca de senha pelo próprio usuário (senha atual + confirmação), evict do cache de autenticação a cada alteração.
- **Auditoria**: tela e API com filtros dinâmicos via Specifications (usuário LIKE parcial, ação, entidade, id, período), paginação e ranking de usuários mais ativos (DTO tipado, compartilhado entre web e REST pelo `AuditoriaConsultaService`).

### 5.7 Paginação
Todas as listas aceitam `?size=` com **clamp [5, 200]** (`Paginacao.clampSize`) — na web e na API — e o fragmento compartilhado de paginação exibe seletor "por página" (20/50/100), contadores e navegação.

---

## 6. Performance (decisões implementadas)

- **Sem N+1**: `@EntityGraph` nas listagens (patrimônios: 2 queries por página em vez de ~41); `default_batch_fetch_size: 16` como rede de segurança global. Protegido por teste de integração — remover um `@EntityGraph` quebra a suíte.
- **Checks O(1)**: `existsBy...` em vez de carregar coleções para verificar existência.
- **Cache Caffeine (TTL 60 s)**: dashboard, UPMs/categorias distintas e UserDetails da API.
- **JDBC batching** (`batch_size: 50`, `order_inserts`) + transação por chunk na importação.
- **PDF incremental**: inventário de ~2k itens caiu de ~90 s para ~1,8 s.
- **Índices de produção** (migration V6, PostgreSQL): `pg_trgm` com GIN em `patrimonio.descricao` e `auditoria_acao.usuario` (buscas `%termo%`), funcionais em `upper(categoria)` e `upper(upm)`.
- Números medidos (base de ~1,9k bens, dev): dashboard 53 ms, listagem 156 ms, CSV 208 ms, XLSX 258 ms, PDF 1,8 s.

---

## 7. Testes (43 testes, 0 falhas)

| Suíte | Cobre |
|---|---|
| `DepreciacaoServiceTest` (8) | estratégias TEMPO/CONSERVAÇÃO, caps, impairment, casos vazios, mapa de VUT |
| `PatrimonioServiceTest` (9) | tombo único, movimentação, baixa, histórico |
| `UsuarioServiceTest` (7) | senha obrigatória, login único, troca de senha, inativação |
| `LotacaoServiceTest` (6) | unicidade (UPM, nome), exclusão protegida, troca de responsável |
| `NormalizadoresTest` (5) | limpeza de dados da planilha, parser de conservação |
| `PatrimonioRepositoryIT` (4) | integração H2+Flyway em banco isolado; garante os `@EntityGraph`, `existsBy` e projeção de tombos |
| `ExcelImportServiceTest` (3) | deduplicação sem SELECT por linha, fallback de chunk, erros descritivos (xlsx real em memória) |
| Smoke test (1) | contexto Spring completo (valida JPQL/configuração no boot) |

Rodar: `./gradlew test`

---

## 8. Como executar

```bash
# Desenvolvimento (H2 em memória + seed automático da planilha)
./gradlew bootRun
# → http://localhost:8080  (login inicial: admin / trocar@123 — trocar no 1º acesso)

# Produção (PostgreSQL)
java -jar app.jar --spring.profiles.active=prod
```

Configurações relevantes (`application.yml` + perfis):
- `app.storage.pasta-raiz` — diretório dos anexos (default `./uploads`).
- `app.importacao.habilitada/caminho` — seed automático no primeiro start (desligado em prod).
- `app.admin.login/senha/nome` — bootstrap do administrador inicial (senha via ambiente em prod).
- `DB_URL/DB_USER/DB_PASSWORD` — datasource do perfil prod.
- A migration V6 executa `CREATE EXTENSION IF NOT EXISTS pg_trgm` — exige privilégio no database (ou peça ao DBA para criar a extensão antes).

### Endpoints REST principais

| Método/Rota | Descrição | Perfil |
|---|---|---|
| `GET /api/patrimonios` (+filtros, paginação) | pesquisa | autenticado |
| `POST/PUT /api/patrimonios`, movimentação, baixa | escrita | conforme RBAC |
| `POST /api/importacao/patrimonios` | importação síncrona | ADMINISTRADOR |
| `POST /api/importacao/patrimonios/async` | importação assíncrona (202 + jobId) | ADMINISTRADOR |
| `GET /api/importacao/jobs/{id}` | status/progresso do job | ADMINISTRADOR |
| `GET /api/admin/auditoria` (+filtros) | trilha de auditoria | ADMINISTRADOR |
| `GET /api/admin/auditoria/usuarios-mais-ativos` | ranking | ADMINISTRADOR |

---

## 9. Robustez e escala (melhorias já aplicadas)

| Tema | Solução implementada |
|---|---|
| SELECT de usuário a cada request da API stateless | Cache de `UserDetails` (Caffeine, TTL 60 s) no `DaoAuthenticationProvider`, com evict imediato em alteração/inativação/troca de senha. |
| Auditoria ocupava 2 conexões por escrita (`REQUIRES_NEW`) | Gravação **assíncrona** (`@Async`): contexto capturado na request, INSERT fora do caminho crítico. |
| Filtros de auditoria com `:param is null or ...` | **Specifications** — só predicados informados entram no SQL, aproveitando os índices da tabela (append-only). |
| Busca textual sem índice | Migration `V6` (só PostgreSQL): `pg_trgm` + GIN trigram e índices funcionais. |
| Paginação fixa | `?size=` com clamp [5, 200] em todas as listas + seletor "por página" no fragmento compartilhado. |
| Importação síncrona | Job assíncrono com UUID, progresso por chunk, tela de status e API 202/status. |

## 10. Limitações conhecidas restantes

- O registro de jobs de importação vive em memória (single-node): reinício descarta o histórico (retenção 2 h). Para cluster, mover para o banco.
- O BCrypt é verificado a cada request Basic da API — para integrações de altíssimo volume, considerar tokens de sessão/JWT.
- A auditoria assíncrona pode perder registros em desligamento abrupto do processo (trade-off aceito para sair do caminho crítico).
- A importação assíncrona processa um arquivo por job sem fila de prioridade — cargas simultâneas concorrem pelo executor padrão do Spring.

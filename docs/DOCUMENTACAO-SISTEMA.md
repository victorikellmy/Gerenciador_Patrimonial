# Gerenciador Patrimonial — Documentação Técnica

Sistema de gestão de bens patrimoniais da Fundação (Fasaúde), cobrindo o ciclo de vida completo do patrimônio: cadastro, movimentação, depreciação contábil, baixa, anexos, relatórios oficiais e trilha de auditoria.

---

## 1. Visão geral

O sistema resolve o problema de controle patrimonial descentralizado (antes em planilha Excel): cada bem tem número de tombo, lotação (UPM + sala), responsável, estado de conservação e valor contábil calculado automaticamente. Duas interfaces sobre o mesmo backend:

- **Interface web (Thymeleaf)** — telas para operação diária: dashboard, cadastro, pesquisa, movimentação, baixa, relatórios, importação e auditoria.
- **API REST (`/api/**`)** — os mesmos casos de uso para integração via scripts/sistemas externos, com HTTP Basic.

---

## 2. Tecnologias

| Camada | Tecnologia | Versão / Observação |
|---|---|---|
| Linguagem | Java | 21 (toolchain Gradle) |
| Framework | Spring Boot | 3.3.5 |
| Build | Gradle | wrapper incluído (`gradlew`) |
| Persistência | Spring Data JPA / Hibernate 6 | `open-in-view: false` |
| Banco (dev) | H2 em memória | `MODE=PostgreSQL`, console em `/h2-console` |
| Banco (prod) | PostgreSQL | perfil `prod` |
| Migrations | Flyway 10.20.1 | `V1`–`V5` em `db/migration` |
| Segurança | Spring Security 6 | 2 filter chains (web + API) |
| Frontend | Thymeleaf + Bootstrap + Bootstrap Icons | fragmentos reutilizáveis (`layout`, `ui-kit`) |
| Excel | Apache POI 5.3.0 | leitura via arquivo temporário; escrita com SXSSF (streaming) |
| PDF | OpenPDF 1.3.34 | tabela incremental para bases grandes |
| Cache | Caffeine + Spring Cache | TTL 60 s (dashboard e dropdowns) |
| Boilerplate | Lombok | `@Getter/@Builder/@RequiredArgsConstructor` |
| Testes | JUnit 5, Mockito, AssertJ, `@DataJpaTest` | 43 testes |

---

## 3. Arquitetura

Monólito em camadas:

```
web/ (Thymeleaf)  controller/ (REST)
        │               │
        └───► service/ ◄┘      regras de negócio, transações, cache
                │
          repository/           Spring Data JPA + Specifications + @EntityGraph
                │
          domain/entity          agregados JPA (Patrimonio é a raiz)
```

Princípios adotados:
- **Regra de negócio na entidade quando é invariante do agregado** (ex.: `Patrimonio.movimentar()` rejeita bem baixado) e **no service quando envolve orquestração** (ex.: validação de tombo único, registro de auditoria).
- **DTOs de entrada/saída** (`dto/request`, `dto/response`) — entidade JPA nunca sai para a view/JSON.
- **Campos derivados não são persistidos**: depreciação, VCL, impairment são calculados sob demanda pelo `DepreciacaoService`.
- Utilitários únicos: `SecurityUtils.usuarioAtualOuSystem()`, `Textos.nullIfBlank/truncar`.

### Modelo de dados (principais entidades)

- **Patrimonio** (raiz): tombo (único, opcional), descrição, categoria/subcategoria, data/valor de compra, conservação, situação, nota fiscal, campos de impairment, lotação, responsável, anexos, histórico de movimentações, colunas de auditoria (criado/atualizado por/em).
- **Lotacao**: chave de negócio `(upm, nome)` única; tipo INTERNO/EXTERNO; responsável atual do setor.
- **Responsavel**: nome, matrícula (única quando informada), lotação, soft delete via `ativo`.
- **Movimentacao**: trilha imutável de trocas de lotação/responsável (origem → destino, quem executou, quando).
- **Usuario**: login único, hash BCrypt, perfil (RBAC), `ativo`.
- **AuditoriaAcao**: trilha append-only de ações (usuário, ação, entidade, descrição do diff, IP).
- **VidaUtilCategoria** e **PercentualConservacao**: tabelas de referência (seeds do Flyway) para o cálculo de depreciação.

### Enums de domínio
- `SituacaoPatrimonio`: ATIVO, BAIXADO, CAUTELADO, EM_APURACAO.
- `Conservacao`: NOVO, OTIMO, BOM, BOM_REGULAR, REGULAR, REGULAR_RUIM, RUIM, INSERVIVEL (com parser tolerante para valores da planilha).
- `Perfil`: ADMINISTRADOR, FISCAL.
- `AcaoAuditoria`: CREATE, UPDATE, DELETE, MOVIMENTAR, BAIXAR, LOGIN, LOGOUT (…).

---

## 4. Segurança

### Autenticação
- **Web**: form login com sessão (`/login`), logout com invalidação de cookie `JSESSIONID`, CSRF **habilitado** em toda a interface web.
- **API (`/api/**`)**: HTTP Basic, **stateless** (sem sessão), CSRF desabilitado apenas nesse escopo — padrão para consumo por scripts.
- Senhas armazenadas exclusivamente como **hash BCrypt (cost 10)** — nunca em claro.
- Login/logout registrados na trilha de auditoria (`AuditoriaLoginListener`).

### Autorização (RBAC)
Dois perfis:

| Ação | ADMINISTRADOR | FISCAL |
|---|---|---|
| Consultar, pesquisar, relatórios | ✔ | ✔ |
| Cadastrar/editar patrimônio, movimentar | ✔ | ✔ |
| Dar baixa, excluir definitivamente | ✔ | ✖ |
| Excluir lotação, inativar responsável | ✔ | ✖ |
| Gestão de usuários (`/usuarios`) | ✔ | ✖ |
| Importação de planilha (`/importacao`) | ✔ | ✖ |
| Trilha de auditoria (`/admin/auditoria`) | ✔ | ✖ |

Implementado em duas camadas: regras por rota no `SecurityConfig` + `@PreAuthorize` nos controllers sensíveis.

### Proteções adicionais
- **Bootstrap seguro do admin**: no primeiro start, se não existe nenhum ADMINISTRADOR ativo, cria `admin` com senha configurável (`app.admin.senha`); loga aviso exigindo troca imediata. Em produção a senha deve vir de variável de ambiente/secret.
- **Situação não é editável via PUT comum** — só pelos endpoints de baixa/movimentação (impede "reviver" bem baixado por request adulterado).
- **Soft delete** de usuários e responsáveis (inativação) preserva a rastreabilidade.
- **Trilha de auditoria dupla**: colunas `@CreatedBy/@LastModifiedBy` (Spring Data Auditing) em cada registro + tabela `auditoria_acao` com descrição do diff campo a campo e IP de origem (com suporte a `X-Forwarded-For` atrás de proxy).
- Registro de auditoria em transação própria (`REQUIRES_NEW`): falha na auditoria não derruba a operação de negócio e vice-versa.
- Uploads restritos por tamanho (`max-file-size: 10MB`, request 25MB); anexos salvos em **filesystem** (fora do banco), servidos com streaming.

---

## 5. Funcionalidades implementadas

### 5.1 Gestão de patrimônio
- CRUD completo com validação Bean Validation nos DTOs.
- **Tombo único** (quando informado) validado na criação e edição.
- **Pesquisa dinâmica** com filtros combináveis (descrição, tombo, UPM, categoria, situação, conservação) via JPA Specifications, paginada.
- **Movimentação**: troca de lotação e/ou responsável; gera registro histórico imutável; bem baixado não pode ser movimentado; exige ao menos um destino.
- **Baixa lógica** (soft delete): situação BAIXADO + data + motivo obrigatório; baixa dupla é rejeitada.
- **Exclusão física** apenas para erro de cadastro (só ADMINISTRADOR), com registro em auditoria.
- **Anexos** por patrimônio (nota fiscal, foto, laudo), armazenados em disco com download via streaming.
- Histórico de movimentações por bem (mais recente primeiro).

### 5.2 Depreciação e impairment (`DepreciacaoService`)
Duas estratégias, decididas pelo dado disponível:

- **TEMPO** (preferida, quando há `dataCompra`): depreciação linear — `depreciacaoAnual = valor / VUT`; VUD em anos = dias corridos / 365,25, com **caps**: nunca deprecia além da VUT nem além do custo; data futura não deprecia.
- **CONSERVAÇÃO** (legado, sem `dataCompra`): usa o % de vida útil decorrida associado ao estado de conservação (tabela de referência) — mantém as métricas dos registros históricos importados da planilha.

Saída única (`CalculoDepreciacao`): VUT, %VUD, VUD/VUR em anos, depreciação acumulada, VCL (valor contábil líquido), depreciação anual e **perda por impairment** = max(0, VCL − valor recuperável) quando há laudo. VUT por categoria e percentuais são pré-carregados em memória no startup (tabelas imutáveis em runtime).

### 5.3 Importação de planilha Excel
- Importa a "Planilha de Reconstituição de Dados" (22 colunas mapeadas), via tela web ou API, com seed automático no primeiro start (idempotente — só roda com banco vazio).
- **Normalização de dados sujos**: UPM ("1BPM" → "1 BPM", remove ordinais), salas corrompidas por fórmula ("CON+B85:D92S." → "CONS."), typos conhecidos, tombos placeholder ("0", "-") viram null.
- **Deduplicação** de tombo contra o banco e dentro da própria planilha (pré-carga em memória — nenhum SELECT por linha).
- **Upsert** de lotações e responsáveis inexistentes durante a carga.
- **Resiliência**: transação por chunk de 100 linhas; se um chunk falha, é reprocessado linha a linha — uma linha ruim não aborta a importação e vira erro descritivo no relatório final (total, importados, ignorados, erros por linha).
- Leitura via arquivo temporário (POI com acesso randômico) — não carrega o .xlsx inteiro em RAM.
- **Execução assíncrona com status**: o upload dispara um job em background (UUID) e redireciona para a tela de acompanhamento, com barra de progresso atualizada por polling — planilhas grandes não bloqueiam a request. Na API: `POST /api/importacao/patrimonios/async` responde 202 com o id do job; `GET /api/importacao/jobs/{id}` retorna estado/progresso/resultado. O endpoint síncrono original permanece para compatibilidade.

### 5.4 Relatórios
- **Inventário completo**: tela paginada (com VCL por item) + downloads **CSV** (UTF-8 com BOM, separador `;` compatível com Excel pt-BR), **XLSX** (streaming SXSSF) e **PDF** (paisagem, tabela incremental). 18 colunas definidas em fonte única (`LinhaInventario`).
- **Relatório de baixas** (tela + CSV/XLSX).
- **Termo de responsabilidade** em PDF por responsável: identificação, texto legal, tabela de bens ativos com total e campos de assinatura.

### 5.5 Dashboard
Métricas agregadas em SQL (sem hidratar entidades): total por situação, valor total dos ativos, depreciação acumulada e VCL totais, agrupamentos por categoria/conservação/top-10 UPMs, últimas 10 movimentações. Cacheado por 60 s.

### 5.6 Administração
- **Usuários**: CRUD com inativação, troca de senha pelo próprio usuário (exige senha atual + confirmação).
- **Auditoria**: tela e API com filtros (usuário, ação, entidade, id, período), paginação e ranking de usuários mais ativos.

---

## 6. Performance (decisões implementadas)

- **Sem N+1**: listagens usam `@EntityGraph` para carregar relações na mesma query (patrimônios: 2 queries por página em vez de ~41); rede de segurança global `default_batch_fetch_size: 16`.
- **Checks O(1)** no banco: `existsBy...` em vez de carregar coleções/tabelas para verificar existência.
- **Cache Caffeine (TTL 60 s)**: dashboard e listas de UPMs/categorias distintas.
- **Exports sem prender conexão**: a consulta roda em transação curta; a escrita no response acontece fora dela.
- **JDBC batching** configurado (`batch_size: 50`, `order_inserts`).
- **PDF incremental**: flush a cada 200 linhas (inventário de ~2k itens: de ~90 s para ~1,8 s).
- Números medidos (base de ~1.9k bens, dev): dashboard 53 ms, listagem 156 ms, CSV 208 ms, XLSX 258 ms, PDF 1,8 s.

---

## 7. Testes (43 testes, 0 falhas)

| Suíte | Cobre |
|---|---|
| `DepreciacaoServiceTest` (8) | estratégias TEMPO/CONSERVAÇÃO, caps, impairment, casos vazios |
| `PatrimonioServiceTest` (9) | tombo único, movimentação, baixa, histórico |
| `UsuarioServiceTest` (7) | senha obrigatória, login único, troca de senha, inativação |
| `LotacaoServiceTest` (6) | unicidade (UPM, nome), exclusão protegida, troca de responsável |
| `NormalizadoresTest` (5) | limpeza de dados da planilha, parser de conservação |
| `ExcelImportServiceTest` (3) | deduplicação, fallback de chunk, erros descritivos (xlsx real em memória) |
| `PatrimonioRepositoryIT` (4) | integração H2+Flyway; garante que os `@EntityGraph` continuam ativos |
| Smoke test (1) | contexto Spring completo (valida JPQL/configuração no boot) |

Rodar: `./gradlew test`

---

## 8. Como executar

```bash
# Desenvolvimento (H2 em memória + seed automático da planilha)
./gradlew bootRun
# → http://localhost:8080  (login inicial: admin / trocar@123 — trocar no 1º acesso)

# Produção
# perfil prod (PostgreSQL): configurar datasource e app.admin.senha via ambiente
java -jar app.jar --spring.profiles.active=prod
```

Configurações relevantes (`application.yml` + perfis):
- `app.storage.pasta-raiz` — diretório dos anexos (default `./uploads`).
- `app.importacao.habilitada/caminho` — seed automático da planilha no primeiro start.
- `app.admin.login/senha/nome` — bootstrap do administrador inicial.

---

## 9. Robustez e escala (melhorias já aplicadas)

| Tema | Solução implementada |
|---|---|
| SELECT de usuário a cada request da API stateless | Cache de `UserDetails` (Caffeine, TTL 60 s) no `DaoAuthenticationProvider`, com evict imediato quando o usuário é alterado/inativado. O BCrypt por request permanece — custo inerente do HTTP Basic. |
| Auditoria ocupava 2 conexões por escrita (`REQUIRES_NEW`) | Gravação **assíncrona** (`@Async`): usuário e IP são capturados no thread da request e o INSERT roda no executor, fora do caminho crítico. |
| Filtros de auditoria com `:param is null or ...` | Migrados para **Specifications** — só os predicados informados entram no SQL, permitindo uso dos índices da tabela (append-only). |
| Busca textual sem índice | Migration `V6` (só PostgreSQL, em `db/vendor/postgresql`): extensão `pg_trgm`, índices GIN trigram em `patrimonio.descricao` e `auditoria_acao.usuario`, índices funcionais `upper(categoria)`/`upper(upm)`. |
| Paginação fixa | `?size=` aceito em todas as listas com clamp [5, 200] (`Paginacao.clampSize`) e seletor "por página" (20/50/100) no fragmento compartilhado. |
| Importação síncrona | Job assíncrono com UUID, progresso por chunk, tela de status com auto-refresh e endpoints REST 202/status (ver § 5.3). |

## 10. Limitações conhecidas restantes

- O registro de jobs de importação vive em memória (single-node): reinício do app descarta o histórico de jobs (retenção de 2 h). Para cluster, seria necessário mover para o banco.
- O BCrypt continua sendo verificado a cada request Basic da API — para integrações de altíssimo volume, considerar tokens de sessão/JWT.
- A auditoria assíncrona pode perder registros em um desligamento abrupto do processo (trade-off aceito para tirar a gravação do caminho crítico).

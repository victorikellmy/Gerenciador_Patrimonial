# Prompt para o Claude do servidor 192.168.1.25 — Frota no Gerenciador Patrimonial

Objetivo: colocar em produção a versão do **Gerenciador Patrimonial** que absorve o sistema
**Transportes FPTO** (módulo Frota), migrar os dados do banco do Transportes e, depois de
validado, desligar o Transportes. Tudo passa a viver numa única plataforma: o Patrimônio.

Cole o bloco abaixo no Claude que roda no servidor (usuário `umbrel`).

---

```
Preciso atualizar o Gerenciador Patrimonial neste servidor Umbrel (192.168.1.25) e fundir nele
o sistema Transportes FPTO. O código já está no GitHub, branch main, em
https://github.com/victorikellmy/Gerenciador_Patrimonial (pasta local /home/umbrel/patrimonial-novo).
O Transportes roda aqui em ~/transportes (container transportes-app na porta 8081) com banco
PostgreSQL "transportes": ou num container próprio (compose.yaml, porta 5433 só no loopback do
host) ou como database dentro do protocantins-db (compose.banco-existente.yaml). Leia primeiro
deploy/README.md, docker-compose.yml, .env.example e docs/PROMPT_SERVIDOR_FROTA.md do Patrimônio.

O QUE MUDA NESTA VERSÃO
- Migrations Flyway V8 (frota), V9 (nota fiscal dos lançamentos), V10 (diárias, cautelas,
  titulares) e V11 (senha provisória / troca obrigatória no primeiro acesso) rodam sozinhas no boot. O Patrimônio agora tem TUDO que o Transportes tinha:
  frota (saídas, abastecimentos, manutenções, cautelas com termo PDF), diárias com o fluxo
  solicitada → confirmada → deferida/indeferida → paga, profissionais, tabela de valores e a
  base de titulares para autocomplete.
- Novos perfis de usuário: MOTORISTA (só lança na frota, com NF obrigatória), GERENTE
  (somente leitura: painel /frota/gestao, dashboard, relatórios), PRESIDENTE (defere/indefere
  diárias) e FINANCEIRO (registra pagamento de diárias). ADMINISTRADOR confirma diárias;
  ADMINISTRADOR/FISCAL abrem e editam.
- Importador do Transportes: com TRANSPORTES_DB_URL/USER/SENHA no .env, o Patrimônio lê o
  banco do Transportes no boot (e pelo botão "Importar do Transportes", como admin) e copia
  veículos, abastecimentos, manutenções, deslocamentos, níveis de diária, profissionais,
  diárias (com histórico), cautelas e os ~4.300 titulares, sem duplicar (idempotente).
- NÃO ligar FROTA_SEED em produção (é só dado de demonstração).

PASSO A PASSO
1. Confirme o ambiente: docker ps (patrimonial-app, patrimonial-db, transportes-app e o banco
   do transportes no ar), versão do PostgreSQL, espaço em disco. Faça backup antes de tudo:
     docker exec patrimonial-db pg_dump -U patrimonial patrimonial | gzip > ~/backup-patrimonial-$(date +%F).sql.gz
   e o mesmo para o banco "transportes" (no container onde ele estiver).
2. Descubra como o Patrimônio alcança o banco do Transportes:
   a) Transportes com banco próprio: a porta 5433 está publicada em 127.0.0.1 do host. O
      docker-compose.yml do Patrimônio já tem extra_hosts host.docker.internal → use
      TRANSPORTES_DB_URL=jdbc:postgresql://host.docker.internal:5433/transportes
   b) Transportes usando o protocantins-db: coloque o serviço app do Patrimônio também na rede
      do banco (networks: external protocantins-db_default) e use
      TRANSPORTES_DB_URL=jdbc:postgresql://protocantins-db:5432/transportes
   Teste a conectividade a partir do container do Patrimônio antes de seguir (ex.: docker run
   --rm --network ... postgres:16-alpine pg_isready -h <host> -p <porta>).
3. Atualize o código e o .env (chmod 600, nunca commitar):
     cd /home/umbrel/patrimonial-novo && git pull origin main
   Acrescente ao .env:
     TRANSPORTES_DB_URL=<url do passo 2>
     TRANSPORTES_DB_USER=transportes
     TRANSPORTES_DB_SENHA=<DB_PASSWORD do .env do Transportes>
     TRANSPORTES_IMPORTAR_NO_BOOT=true
   Mantenha as variáveis que já existem (DB_*, APP_PORT, INTEGRACAO_ALMOXARIFADO_SENHA).
4. Suba: docker compose -p gerenciador-patrimonial up -d --build app
   Acompanhe docker logs -f patrimonial-app até "Started GerenciadorPatrimonialApplication".
   No log devem aparecer: Flyway aplicando V8__frota e V9__frota_nota_fiscal, e a linha
   "Importação do Transportes concluída: Resultado[veiculosNovos=..., abastecimentos=..., ...]".
   Se aparecer "Falha ao ler o banco do Transportes", corrija a URL/senha/rede e reinicie só o app.
5. Confira no banco do Patrimônio: select count(*) from veiculo; from frota_abastecimento;
   from frota_manutencao; from frota_deslocamento; from frota_cautela; from profissional;
   from diaria; from diaria_historico; from titular — os números devem bater com as tabelas
   veiculo/abastecimento/manutencao/deslocamento/cautela/profissional/diaria/diaria_historico/
   titular do Transportes (veículos inativos duplicados do legado são ignorados de propósito;
   nivel_diaria já vem na migration e é casada por id). Confira também que cada veículo virou
   um patrimônio (select count(*) from patrimonio where categoria = 'VEICULO').
6. Usuários (como admin, em Administração → Usuários do Patrimônio). Os logins do Transportes
   NÃO migram (o Patrimônio tem a própria tabela de usuários); crie de novo, mapeando o perfil:
   Transportes "Motorista" → MOTORISTA; "Coordenação" → FISCAL; "Presidente" → PRESIDENTE;
   "Financeiro" → FINANCEIRO; "Administrador" → ADMINISTRADOR; gerentes → GERENTE.
   A senha que você digitar no cadastro é PROVISÓRIA: no primeiro login a pessoa é obrigada a
   definir a própria senha antes de ver qualquer tela (migration V11). Entregue login + senha
   provisória a cada um; não precisa trocar depois. O mesmo vale se o admin redefinir a senha
   de alguém. Atenção: o admin inicial criado pelo sistema também entra com senha provisória.
   Ajuste a lotação/responsável dos veículos importados (entram em "FROTA / <cidade>" com
   responsável "SETOR DE TRANSPORTES - FPTO").
7. Validação funcional: motorista registra saída, chegada, abastecimento com foto da NF,
   manutenção e uma cautela (imprime o termo); fiscal abre uma diária para um profissional
   importado; admin confirma; presidente defere; financeiro paga e abre /diarias/pagas;
   gerente abre /frota/gestao e confere "na rua", "em manutenção", gastos e revisões.
8. Período de convivência: deixe o Transportes no ar em modo leitura por 1 a 2 semanas
   (avise os motoristas para lançar só no Patrimônio). A importação é idempotente: se alguém
   lançar no Transportes nesse período, basta clicar "Importar do Transportes" no Patrimônio.
9. Desligamento do Transportes (só quando eu confirmar): faça um último "Importar do
   Transportes", um último backup do banco transportes, então docker compose down do
   transportes-app (NÃO remova o volume do banco por enquanto), desative o
   transportes-autostart.service se existir, e remova/redirecione a rota 8081 no reverse proxy.
   Deixe TRANSPORTES_IMPORTAR_NO_BOOT=false no .env do Patrimônio depois disso.

REGRAS
- Antes de qualquer comando destrutivo (down, rm, drop, rmi, apagar volume) me pergunte.
- Não edite migrations nem desligue o Flyway/ddl-auto=validate.
- Não exiba senhas em logs ou no resumo.
- No final me entregue: versão/commit em produção, variáveis adicionadas (sem valores),
  números da importação, usuários criados e o que ficou pendente.
```

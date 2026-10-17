# Prompt para o Claude do servidor 192.168.1.25 — Front-end React do Gerenciador Patrimonial

Objetivo: publicar a versão do **Gerenciador Patrimonial** cujo front-end foi migrado para
**React** (SPA servida pelo próprio Spring em `/app`). O back-end, o banco e as migrations
não mudam; o que muda é o build (agora precisa de Node dentro do Docker) e a forma como os
usuários entram no sistema.

Cole o bloco abaixo no Claude que roda no servidor (usuário `umbrel`).

---

```
Preciso atualizar o Gerenciador Patrimonial neste servidor Umbrel (192.168.1.25) para a
versão com front-end React. O código está no GitHub, branch main, em
https://github.com/victorikellmy/Gerenciador_Patrimonial (clone em /home/umbrel/deploy/patrimonial/repo; /home/umbrel/patrimonial-novo é symlink para a
pasta de produção, que NÃO é um repositório git).
Leia primeiro deploy/README.md, docker-compose.yml, Dockerfile, .env.example e
docs/PROMPT_SERVIDOR_REACT.md.

O QUE MUDA NESTA VERSÃO
- Nova SPA React em /app (pasta frontend/ do repositório). O Spring continua servindo as
  telas antigas (Thymeleaf) nas rotas de sempre (/, /frota, /diarias...) e a SPA em /app.
  Durante a homologação as duas convivem; a SPA conversa só com a API /api/**.
- O Dockerfile instala Node/npm no estágio de build e o Gradle compila a SPA
  (tarefas npmInstall → buildFrontend → processResources). O build do container demora
  mais (~2 a 4 min a mais na primeira vez, por causa do npm ci). Não precisa de Node no host.
- Login da SPA: POST /api/auth/login (JSON) abre a mesma sessão por cookie do site; o cookie
  de sessão passou a ter SameSite=Lax (server.servlet.session.cookie.same-site). Nada a
  configurar no .env por causa disso.
- Nenhuma migration nova (continua na V11). Nenhuma variável de ambiente nova.
- Rotas /app e /app/** são públicas no Spring Security (a própria SPA redireciona para o
  login quando /api/me responde 401). A API continua protegida como antes.

PASSO A PASSO
1. Confirme o ambiente: docker ps (patrimonial-app e patrimonial-db no ar), espaço em disco
   (o build com Node usa ~1 GB temporário). Backup antes de tudo:
     docker exec patrimonial-db pg_dump -U patrimonial patrimonial | gzip > ~/backup-patrimonial-$(date +%F).sql.gz
2. Atualize o código:
     cd /home/umbrel/deploy/patrimonial/repo && git pull origin main
   ATENÇÃO: o motor de deploy automático preserva .env, docker-compose.yml e Dockerfile
   (PRESERVAR no patrimonial.conf). Esta versão MUDA o Dockerfile (Node no build): copie o
   Dockerfile novo do repo para a pasta de produção à mão antes do build, senão o
   ":npmInstall" falha com "A problem occurred starting process npm" e o motor faz rollback.
   Sincronize o código com o mesmo rsync do motor, preservando .env e docker-compose.yml.
   Confira que existem a pasta frontend/ (com package.json e package-lock.json) e o arquivo
   .dockerignore na raiz. Não copie node_modules para o servidor.
3. Rebuild e suba:
     docker compose -p gerenciador-patrimonial up -d --build app
   Acompanhe docker compose -p gerenciador-patrimonial logs -f app até ver "Started
   GerenciadorPatrimonialApplication". Se o build falhar em "npm ci", verifique se o
   servidor tem acesso ao registry.npmjs.org (proxy/firewall); se falhar em "Task
   :buildFrontend", mande o log completo.
4. Validação (no host, com curl, e depois num navegador):
     curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/app           # 200
     curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/app/frota/1   # 200 (index da SPA)
     curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/api/me        # 401 (sem sessão)
     curl -s -H "Content-Type: application/json" -d '{"login":"USUARIO","senha":"SENHA"}' \
          -c /tmp/ck -w "\n%{http_code}\n" http://localhost:8082/api/auth/login    # 200 + JSON do usuário
     curl -s -b /tmp/ck http://localhost:8082/api/me                               # 200
     rm -f /tmp/ck
   No navegador: http://192.168.1.25:8082/app → tela de login da SPA → entrar com um usuário
   de cada perfil e conferir: ADMINISTRADOR cai no dashboard; MOTORISTA cai em /app/frota e só
   vê a frota; GERENTE cai em /app/frota/gestao (somente leitura); PRESIDENTE e FINANCEIRO caem
   em /app/diarias. Usuário com senha provisória é levado a /app/minha-conta/senha?obrigatoria
   e não consegue navegar antes de trocar.
   Conferir também: abrir a ficha de um veículo, registrar uma saída e a chegada, anexar uma
   NF num abastecimento como MOTORISTA (é obrigatória), abrir uma diária e deferir/pagar com
   os perfis certos, exportar Excel/PDF em /app/frota/gestao e /app/relatorios, e o tema
   escuro (botão da lua na barra superior).
5. Se algo da SPA falhar, as telas antigas continuam no ar nas rotas sem /app — use-as
   enquanto reportar o problema. Para voltar à versão anterior do container:
     git log --oneline -5  → git checkout <commit anterior> → up -d --build app
6. Quando a SPA for validada pelo usuário, a próxima etapa (não fazer agora) é apontar a
   raiz "/" para /app e remover os templates Thymeleaf.

SEGURANÇA
- Não comitar .env. Não imprimir senhas em logs ou no chat.
- Senhas provisórias de usuários novos: criar pelo ADMINISTRADOR em /app/usuarios com
  "Exigir troca de senha no próximo acesso" marcado (padrão).
```

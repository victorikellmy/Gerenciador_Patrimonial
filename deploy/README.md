# Deploy em produção — Gerenciador Patrimonial

Arquitetura de dois servidores:

| Servidor | IP | Papel |
|---|---|---|
| Aplicação | `192.168.1.25` | Backend Spring Boot + PostgreSQL (Docker) |
| Armazenamento | `192.168.1.35` | NFS server expondo `/srv/patrimonio/uploads` |

---

## Passo 1 — Servidor de armazenamento (192.168.1.35)

Configura uma única vez. Roda como root no servidor de armazenamento:

```bash
sudo ./deploy/setup-storage-nfs.sh
```

O script: instala `nfs-kernel-server`, cria `/srv/patrimonio/uploads` com `chown 1000:1000` (o UID do usuário `app` no container), adiciona a linha em `/etc/exports` autorizando apenas o `192.168.1.25` e dá `exportfs -ra`.

**Firewall:** libere TCP/UDP **2049** entre `192.168.1.25` e `192.168.1.35`.

Validação a partir do servidor de aplicação:

```bash
showmount -e 192.168.1.35
# Deve listar: /srv/patrimonio/uploads 192.168.1.25
```

---

## Passo 2 — Servidor de aplicação (192.168.1.25)

### Pré-requisitos

```bash
# Debian/Ubuntu
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin nfs-common

# RHEL/Alma
sudo dnf install -y docker docker-compose-plugin nfs-utils
sudo systemctl enable --now docker
```

### Configuração

```bash
git clone <repo> /opt/gerenciador-patrimonial
cd /opt/gerenciador-patrimonial

cp .env.example .env
# Edite .env e defina DB_PASSWORD (senha forte).
```

### Subir a stack

```bash
./deploy/deploy.sh
```

O script:
1. Verifica se `docker`, `mount.nfs` e `.env` existem.
2. Faz ping no `192.168.1.35` (sanity check de rede).
3. `docker compose up -d --build` — sobe Postgres + app.
4. Aguarda o app responder no `localhost:80`.

O volume `uploads_nfs` é montado **dinamicamente pelo Docker** via driver local com opções NFS (ver `docker-compose.yml`). Não é preciso editar `/etc/fstab` — o mount sobe e desce com a stack.

### Logs / restart

```bash
./deploy/deploy.sh --logs       # tail dos logs do app
./deploy/deploy.sh --rebuild    # rebuild forçado da imagem
docker compose restart app
docker compose down             # derruba tudo (volume NFS é desmontado)
```

---

## Migrations Flyway

Aplicadas automaticamente no startup do app (`spring.flyway.enabled=true`). Sequência atual:

| Versão | Descrição |
|---|---|
| `V1__init_schema.sql`           | Schema inicial (lotacao, responsavel, patrimonio, anexo, movimentacao, usuario) |
| `V2__seed_reference_data.sql`   | Tabelas de domínio (VUT por categoria, % VUD por conservação) |
| `V3__novos_campos_patrimonio.sql` | Campos do laudo de impairment (valor_recuperavel, conclusao_impairment, observacao, link_referencia) |
| `V4__subcategoria_patrimonio.sql` | Coluna `subcategoria` |
| `V5__auditoria_acao.sql`        | **Tabela de logs de auditoria** (usuario, acao, entidade, descricao, ip_origem, data_hora) |

Para verificar manualmente o estado:

```bash
docker compose exec db psql -U patrimonial -d patrimonial -c "\dt"
docker compose exec db psql -U patrimonial -d patrimonial -c "select * from flyway_schema_history order by installed_rank;"
```

---

## Checklist pós-deploy

- [ ] `curl http://192.168.1.25/login` retorna HTTP 200.
- [ ] Login funciona com o usuário admin (criado pelo `AdminBootstrapRunner` no primeiro start).
- [ ] Teste de upload de anexo: faça upload em um patrimônio → confira no servidor de armazenamento que o arquivo aparece em `/srv/patrimonio/uploads/patrimonio/<id>/<ano-mes>/`.
- [ ] `GET /api/admin/auditoria` (com login admin) retorna a paginação JSON.
- [ ] Tabela `auditoria_acao` recebe registros após qualquer CRUD de patrimônio.

---

## Troubleshooting

**`mount.nfs: access denied by server`** — verifique a linha em `/etc/exports` do `192.168.1.35` e rode `sudo exportfs -ra` lá.

**`Operation not permitted`** ao gravar em `/uploads` no container — confira se o `chown 1000:1000` foi aplicado no diretório exportado.

**App sobe e cai com erro de Flyway** — geralmente é um schema pré-existente sem histórico Flyway. Rode `docker compose exec db psql -U patrimonial -d patrimonial -c "select version from flyway_schema_history;"` e, se necessário, faça baseline: `docker compose run --rm app java -jar app.jar -Dspring.flyway.baseline-on-migrate=true`.

**Performance lenta de upload** — troque `soft` por `hard` no `docker-compose.yml` (mais resiliente) ou aumente `timeo`. O `soft` é o default aqui para evitar travar a aplicação se o NFS cair.

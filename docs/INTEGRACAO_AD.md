# Login pela rede da Fundação (Active Directory FPTO.LOCAL)

O Patrimônio aceita o **mesmo usuário e senha da rede** (o login do Windows) do domínio
**FPTO.LOCAL**, cujo controlador é o Samba AD na VM **192.168.13.40** (`dc01.fpto.local`).

## Como funciona

| Situação | O que acontece |
|---|---|
| Pessoa com conta na rede entra pela 1ª vez | O sistema confere a senha no AD e cria o cadastro como **Usuário padrão** (entra, mas não vê nenhum módulo). |
| Administrador escolhe o acesso em **Usuários › Grupos da rede (AD)** | O sistema grava direto no AD: inclui a conta no grupo `Patrimonio-<Perfil>` e tira dos outros grupos do Patrimônio. A sessão aberta da pessoa é encerrada e no próximo login vale o novo perfil. |
| Grupo alterado direto no AD (RSAT, `samba-tool`) | Vale no próximo login da pessoa. |
| Cadastro local antigo com o mesmo login | No primeiro login pela rede ele passa a usar a senha da rede. Se ainda não estiver em nenhum grupo, o perfil que já tinha é gravado no AD (ninguém perde acesso). Depois disso a senha antiga do sistema deixa de valer. |
| Conta desativada no AD | Não entra. |
| Pessoa desativada só no Patrimônio (Contas no sistema › inativar) | Não entra no Patrimônio, mesmo ativa na rede. |
| AD fora do ar | Contas da rede recebem "não foi possível conferir a senha"; **contas locais** (ex.: `admin`) continuam entrando pela senha do sistema. Mantenha o `admin` local para emergências. |
| Conta técnica `integracao.almoxarifado` | Nunca usa o AD. |

Grupos (na OU `OU=Patrimonio,DC=fpto,DC=local`):

| Grupo no AD | Perfil no Patrimônio |
|---|---|
| `Patrimonio-Administrador` | Administrador |
| `Patrimonio-Presidente` | Presidente |
| `Patrimonio-Financeiro` | Financeiro |
| `Patrimonio-Fiscal` | Fiscal |
| `Patrimonio-Gerente` | Gerente |
| `Patrimonio-Motorista` | Motorista |
| `Patrimonio-Abastecer` | permissão extra "pode registrar abastecimentos" |
| (nenhum) | Usuário padrão |

Se alguém estiver em dois grupos de perfil, vale o primeiro da tabela.

O login aceita `joao.silva`, `FPTO\joao.silva` ou `joao.silva@fpto.local`. Vale para o site
e para o app Android (HTTP Basic). A senha é trocada no Windows (Ctrl+Alt+Del → Alterar uma senha).

## 1. Preparar o AD (na VM 192.168.13.40, como root)

```bash
# OU dos grupos do Patrimônio
samba-tool ou create "OU=Patrimonio,DC=fpto,DC=local"

# Conta de serviço (senha forte, sem expirar)
samba-tool user create svc-patrimonio --random-password --description="Servico do sistema Patrimonio"
samba-tool user setpassword svc-patrimonio          # digite a senha que vai para AD_SENHA_SERVICO
samba-tool user setexpiry svc-patrimonio --noexpiry

# SID da conta de serviço
samba-tool user show svc-patrimonio --attributes=objectSid
```

Permissões só na OU do Patrimônio (troque `S-1-5-21-...` pelo SID acima):

```bash
# ler/gravar o atributo "member" dos grupos dentro da OU
samba-tool dsacl set --objectdn="OU=Patrimonio,DC=fpto,DC=local" \
  --sddl="(OA;CI;RPWP;bf9679c0-0de6-11d0-a285-00aa003049e2;bf967a9c-0de6-11d0-a285-00aa003049e2;S-1-5-21-...)"
# criar grupos na OU (para o botão "Criar grupos que faltam")
samba-tool dsacl set --objectdn="OU=Patrimonio,DC=fpto,DC=local" \
  --sddl="(OA;CI;CC;bf967a9c-0de6-11d0-a285-00aa003049e2;;S-1-5-21-...)"
samba-tool dsacl get --objectdn="OU=Patrimonio,DC=fpto,DC=local"   # conferir
```

(`bf9679c0-…` = atributo `member`; `bf967a9c-…` = classe `group`.) A conta de serviço só lê
o diretório e mexe nos grupos dessa OU; ela **não** consegue alterar Domain Admins nem outros grupos.

Se preferir não dar a permissão de criar grupos, crie-os à mão e dê só a primeira permissão:

```bash
for g in Administrador Presidente Financeiro Fiscal Gerente Motorista Abastecer; do
  samba-tool group add "Patrimonio-$g" --groupou="OU=Patrimonio"
done
```

## 2. Certificado TLS (LDAPS, porta 636)

O Samba recusa senha em LDAP sem criptografia, então o Patrimônio usa `ldaps://`. O certificado
atual do DC é o autogerado pelo Samba (`CN=DC01.fpto.local`, válido até **08/2028**). Copie a CA
do Samba para o servidor do Patrimônio (.25), na pasta do compose:

```bash
# na VM 192.168.13.40
cat /var/lib/samba/private/tls/ca.pem
# no servidor .25, na pasta do docker-compose do Patrimônio
mkdir -p config && nano config/ad-ca.pem     # cole o conteúdo
```

Quando o certificado do Samba for renovado/trocado, copie a CA nova e reinicie o app.

## 3. Configurar o servidor .25

O motor de deploy **preserva `.env` e `docker-compose.yml`**: aplique à mão.

No `docker-compose.yml` de produção, serviço `app`, acrescente em `environment:`

```yaml
      AD_HABILITADO:      ${AD_HABILITADO:-false}
      AD_URLS:            ${AD_URLS:-ldaps://192.168.13.40:636}
      AD_DOMINIO:         ${AD_DOMINIO:-fpto.local}
      AD_BASE:            ${AD_BASE:-DC=fpto,DC=local}
      AD_USUARIO_SERVICO: ${AD_USUARIO_SERVICO:-svc-patrimonio@fpto.local}
      AD_SENHA_SERVICO:   ${AD_SENHA_SERVICO:-}
      AD_OU_GRUPOS:       ${AD_OU_GRUPOS:-OU=Patrimonio,DC=fpto,DC=local}
      AD_CERTIFICADO_CA:  ${AD_CERTIFICADO_CA:-/config/ad-ca.pem}
```

e em `volumes:` a linha `- ./config:/config:ro`. No `.env`:

```
AD_HABILITADO=true
AD_SENHA_SERVICO=<senha da svc-patrimonio>
```

Teste a rede antes de ligar (do servidor .25): `nc -zv 192.168.13.40 636`.
Depois `docker compose up -d app` e, logado como `admin`, abra **Usuários › Grupos da rede (AD)**:
a página mostra se conectou, quais grupos faltam (botão "Criar grupos que faltam") e lista as contas.

Outras variáveis: `AD_URLS` aceita mais de um servidor separado por vírgula (failover, todos `ldaps://`
ou todos `ldap://`), `AD_PREFIXO_GRUPO` (padrão `Patrimonio-`), `AD_TIMEOUT_MS` (padrão 5000).
`AD_CONFIAR_EM_QUALQUER_CERTIFICADO=true` desliga a verificação do certificado: só para teste de bancada.

## 4. Primeiros passos depois de ligar

1. Entre com a **sua conta da rede**: você aparece como Usuário padrão (ou com o perfil que já tinha,
   se havia um cadastro local com o mesmo login).
2. Com o `admin` local, em Grupos da rede, dê **Administrador** à sua conta da rede.
3. Libere os demais usuários pela mesma tela. Cadastros locais antigos com o mesmo login migram sozinhos no primeiro login.

## Código

`service/dominio/DiretorioAd` (LDAP, UnboundID), `DominioUsuarioService` (cadastro, migração, grupos),
`security/DominioAuthenticationProvider` (antes do login local no `ProviderManager` de `SecurityConfig`),
`controller/DominioController` (`/api/admin/dominio/**`), tela `frontend/src/pages/UsuariosRede.tsx`.
Migration `V16__usuario_origem_dominio.sql` (`usuario.origem` LOCAL/DOMINIO). Teste: `DominioLoginFluxoTest`
(servidor LDAP em memória com a estrutura do Samba AD).

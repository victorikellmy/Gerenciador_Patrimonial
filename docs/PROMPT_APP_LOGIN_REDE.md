# Prompt para o app Android (Patrimônio FPTO): login pela rede

Cole no projeto `app_patrimonio`:

> A API do Patrimônio passou a aceitar o usuário e a senha da rede da Fundação (AD FPTO.LOCAL)
> no mesmo HTTP Basic de sempre. Ajuste o app:
>
> 1. `GET /api/me` (e o retorno do login) ganhou o campo `origem`: `"LOCAL"` (senha do sistema) ou
>    `"DOMINIO"` (senha da rede). Campo novo, aditivo: o parser deve ignorar campos desconhecidos.
> 2. Existe um perfil novo, `"USUARIO"` ("Usuário padrão"): conta da rede que ainda não foi liberada.
>    Ele só acessa `/api/me/**`; o resto responde 403. Trate o perfil desconhecido sem quebrar e mostre:
>    "Sua conta da rede ainda não tem acesso ao Patrimônio. Peça ao administrador para liberar em
>    Usuários › Grupos da rede." com um botão Sair.
> 3. Na tela de login, mostre a dica "Use o mesmo usuário e senha da rede (Windows)". O login pode
>    ser `joao.silva`, `FPTO\joao.silva` ou `joao.silva@fpto.local`.
> 4. Se `origem == "DOMINIO"`, esconda "Trocar senha" (a API responde 409): a senha é trocada no Windows.
> 5. Se o servidor da rede estiver fora do ar, o login de conta da rede falha com 401 no Basic. Mantenha a
>    mensagem genérica de usuário/senha inválidos.
>
> Mudar o perfil de alguém (pela tela Grupos da rede) encerra a sessão dela: a próxima chamada recebe 401
> e o app deve voltar para o login, como já faz quando a senha é trocada.

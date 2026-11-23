# Prompt para a sessão do app Android: novo requerimento de diárias

Cole o texto abaixo na sessão do Claude que cuida do app (`C:\Users\victorikellmy\AndroidStudioProjects\app_patrimonio`).

```text
O portal do Patrimônio mudou o contrato de abertura de diárias (branch homologacao do repositório
C:\Users\victorikellmy\IdeaProjects\Gerenciador_Patrimonial, migration V14). Preciso que o app acompanhe.
Hoje (versão 1.4) o formulário de diária do app envia o contrato antigo e o servidor responde 409 com a
mensagem "Esta versão do aplicativo não envia os dados do novo Requerimento de Diárias...". Listar, ver,
confirmar, deferir, indeferir e pagar continuam funcionando sem mudança.

Leia no portal, antes de mexer no app:
- dto/request/DiariaRequest.java, dto/response/DiariaResponse.java, dto/response/ProfissionalResponse.java
- service/CalculoDiarias.java (regra da quantidade) e service/DiariaService.java (método validar)
- frontend/src/pages/diarias/Form.tsx (tela de referência)

O que muda no app

1. POST /api/diarias e PUT /api/diarias/{id} passam a receber:
   profissionalId (opcional), numeroRequerimento (opcional, só dígitos e "/"; em branco o servidor numera),
   dataRequerimento (yyyy-MM-dd, opcional), nome, matricula (dígitos, "/" e "-"), cargo, lotacao,
   cpf (11 dígitos), rg (opcional), orgaoExpedidor, nivelId (opcional),
   origem, itinerario (destino), partida, horaSaida (HH:mm), retorno, horaChegada (HH:mm),
   meioTransporte (VEICULO_OFICIAL | AEREO | RODOVIARIO | OUTRO), recurso (PROPRIO | CONVENIO | OUTRA),
   finalidade (até 350 caracteres), banco (3 dígitos), agencia, contaCorrente, pix (sem espaços),
   tipoDiaria (CAPITAL | INTERIOR_ESTADO | INTERIOR_OUTRO_ESTADO), valorUnitario.
   Todos obrigatórios, menos os marcados como opcionais. numeroDiarias NÃO é mais enviado:
   o servidor calcula pelas datas e horários. Erros de regra voltam como 409 com "mensagem".

2. Os dados do profissional vão no próprio pedido e alimentam o cadastro. Para preencher o formulário:
   GET /api/diarias/profissionais/busca?termo=  (nome, CPF com ou sem pontuação, ou matrícula; até 10;
   só ADMINISTRADOR e FISCAL). Ao escolher um resultado, preencha tudo (inclusive banco, agencia,
   contaCorrente, pix e cargo) e mande o profissionalId. Sem resultado, o usuário digita os dados e o
   servidor cria o cadastro ao salvar.

3. Prévia da quantidade: GET /api/diarias/calculo?partida=&horaSaida=&retorno=&horaChegada=
   devolve integrais, alimentacao, quantidade, duracaoMin, restoMin, inicioResto, refeicoes e
   avisoPortaria (alerta do art. 7º, só informativo). Mostre quantidade, total (quantidade × valor
   unitário) e a memória de cálculo. Se quantidade for 0, não deixe salvar.

4. O valor unitário é sugerido pela tabela: nível do profissional + tipo de diária
   (valorCapitais / valorInteriorEstado / valorInteriorOutroEstado de GET /api/diarias/niveis?ativos=true).
   O usuário pode alterar.

5. DiariaResponse ganhou (aditivo): numeroRequerimento, origem, tipoDiaria, profissionalCargo,
   profissionalNivelId, banco, agencia, contaCorrente, pix e calculo. Mostre "Diária nº {numeroRequerimento}"
   no lugar do id e o cargo em vez do nível. Acrescente OUTRO e OUTRA aos enums MeioTransporte e
   RecursoDiaria e crie o enum TipoDiaria. ProfissionalResponse/Request ganharam cargo, banco, agencia,
   contaCorrente e pix; nivelId virou opcional.

6. GET /api/diarias/{id}/pdf agora devolve o Requerimento de Diárias no modelo do Anexo II da Portaria
   001/2019 (é o documento que vai para o SGD). O resumo antigo com histórico está em
   GET /api/diarias/{id}/extrato. Renomeie o botão para "Requerimento em PDF".

Ao terminar: suba a versão do app (versionCode e versionName), rode os testes, gere o APK assinado e me
diga o que mudou. Não publique o APK no portal sem eu confirmar.
```

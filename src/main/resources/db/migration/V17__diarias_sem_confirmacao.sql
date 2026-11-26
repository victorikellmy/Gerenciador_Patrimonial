-- =============================================================================
-- V17 — Fim da etapa de confirmação das diárias pelo administrador.
-- Fluxo novo: SOLICITADO → DEFERIDO/INDEFERIDO (Presidente) → PAGO (Financeiro).
-- Diárias que estavam "Confirmadas" voltam a "Solicitada", que agora é a fila
-- do Presidente. O histórico antigo continua citando CONFIRMADO; a mudança fica
-- registrada numa linha nova do histórico de cada diária afetada.
-- =============================================================================
INSERT INTO diaria_historico (diaria_id, de_status, para_status, usuario, instante, observacao)
SELECT id, 'CONFIRMADO', 'SOLICITADO', 'Sistema', CURRENT_TIMESTAMP,
       'Etapa de confirmação extinta: diária encaminhada direto para a decisão do Presidente.'
FROM diaria
WHERE status = 'CONFIRMADO';

UPDATE diaria SET status = 'SOLICITADO' WHERE status = 'CONFIRMADO';

-- =============================================================================
-- V15 — Tabela de valores de diárias do Anexo I da Portaria 001/2019 GAB. PRES
-- (substitui a da Portaria 004/2018, que veio do sistema Transportes).
--
-- Os níveis que não fazem parte da tabela nova ficam INATIVOS (não são apagados:
-- podem ser consultados e reativados em Diárias > Tabela de valores). Os cinco
-- níveis novos só são inseridos se ainda não houver um nível ativo com os mesmos
-- três valores (caso alguém já os tenha cadastrado pela tela).
--
-- Nenhuma diária muda: cada uma guarda o valor unitário com que foi pedida.
-- Profissionais ligados a um nível inativo passam a aparecer sem nível (a API
-- esconde níveis inativos) até que o nível novo seja escolhido num requerimento.
-- =============================================================================
UPDATE nivel_diaria SET ativo = FALSE
 WHERE NOT ( (valor_capitais = 626.00 AND valor_interior_estado = 394.00 AND valor_interior_outro_estado = 517.00)
          OR (valor_capitais = 476.00 AND valor_interior_estado = 318.00 AND valor_interior_outro_estado = 408.00)
          OR (valor_capitais = 427.00 AND valor_interior_estado = 281.00 AND valor_interior_outro_estado = 367.00)
          OR (valor_capitais = 381.00 AND valor_interior_estado = 261.00 AND valor_interior_outro_estado = 319.00)
          OR (valor_capitais = 243.00 AND valor_interior_estado = 163.00 AND valor_interior_outro_estado = 207.00) );

INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado)
SELECT 'Diretor Presidente; Diretor Administrativo; Diretor Financeiro; Conselheiro (Fiscal / Curador)', 626.00, 394.00, 517.00
 WHERE NOT EXISTS (SELECT 1 FROM nivel_diaria WHERE ativo = TRUE
                    AND valor_capitais = 626.00 AND valor_interior_estado = 394.00 AND valor_interior_outro_estado = 517.00);

INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado)
SELECT 'Supervisor; Gerente; Coordenador; Assessor (Jurídico / Comunicação / Contábil)', 476.00, 318.00, 408.00
 WHERE NOT EXISTS (SELECT 1 FROM nivel_diaria WHERE ativo = TRUE
                    AND valor_capitais = 476.00 AND valor_interior_estado = 318.00 AND valor_interior_outro_estado = 408.00);

INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado)
SELECT 'Analista; Enfermeiro; Médico; Odontólogo; Assistente Social; e Fisioterapeuta', 427.00, 281.00, 367.00
 WHERE NOT EXISTS (SELECT 1 FROM nivel_diaria WHERE ativo = TRUE
                    AND valor_capitais = 427.00 AND valor_interior_estado = 281.00 AND valor_interior_outro_estado = 367.00);

INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado)
SELECT 'Assistente; Técnico em enfermagem; Técnico Bucal; Motorista; e Prestadores de Serviço à FPTO', 381.00, 261.00, 319.00
 WHERE NOT EXISTS (SELECT 1 FROM nivel_diaria WHERE ativo = TRUE
                    AND valor_capitais = 381.00 AND valor_interior_estado = 261.00 AND valor_interior_outro_estado = 319.00);

INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado)
SELECT 'Auxiliar; Menor Aprendiz; e Estagiário', 243.00, 163.00, 207.00
 WHERE NOT EXISTS (SELECT 1 FROM nivel_diaria WHERE ativo = TRUE
                    AND valor_capitais = 243.00 AND valor_interior_estado = 163.00 AND valor_interior_outro_estado = 207.00);

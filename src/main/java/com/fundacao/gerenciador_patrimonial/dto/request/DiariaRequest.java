package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoDiaria;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Requerimento de diária (Anexo II da Portaria 001/2019 GAB. PRES).
 *
 * <p>Os dados do profissional vêm no próprio pedido: o serviço localiza o cadastro
 * ({@code profissionalId} ou CPF), cria se não existir e o atualiza com o que foi informado.
 * A quantidade de diárias não é enviada: o servidor apura a partir das datas e dos horários.</p>
 *
 * <p>A obrigatoriedade e o formato de cada campo são conferidos no {@code DiariaService}
 * (mensagens na ordem do formulário); aqui ficam só os limites de tamanho.</p>
 */
public record DiariaRequest(
        // --- 1. identificação ---
        Long profissionalId,
        @Size(max = 20, message = "Número do requerimento: no máximo 20 caracteres") String numeroRequerimento,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataRequerimento,
        @Size(max = 160) String nome,
        @Size(max = 30) String matricula,
        @Size(max = 120) String cargo,
        @Size(max = 60) String lotacao,
        @Size(max = 14) String cpf,
        @Size(max = 30) String rg,
        @Size(max = 30) String orgaoExpedidor,
        /** Linha da tabela de valores (opcional), para sugerir o valor unitário nas próximas vezes. */
        Long nivelId,

        // --- 2. missão e deslocamento ---
        @Size(max = 120) String origem,
        /** Destino / itinerário. */
        @Size(max = 200) String itinerario,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate partida,
        @DateTimeFormat(pattern = "HH:mm") LocalTime horaSaida,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate retorno,
        @DateTimeFormat(pattern = "HH:mm") LocalTime horaChegada,
        MeioTransporte meioTransporte,
        RecursoDiaria recurso,
        @Size(max = 350, message = "Finalidade da viagem: no máximo 350 caracteres") String finalidade,
        @Size(max = 3) String banco,
        @Size(max = 15) String agencia,
        @Size(max = 20) String contaCorrente,
        @Size(max = 120) String pix,

        // --- 3. apuração ---
        TipoDiaria tipoDiaria,
        BigDecimal valorUnitario
) {
    /**
     * Pedido no formato antigo (app Android até a versão 1.4): só o id do profissional, sem os
     * dados que o novo requerimento exige.
     */
    public boolean formatoAntigo() {
        return nome == null && cpf == null && tipoDiaria == null && origem == null && banco == null;
    }
}

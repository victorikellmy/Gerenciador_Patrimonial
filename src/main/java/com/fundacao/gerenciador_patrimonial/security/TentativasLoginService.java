package com.fundacao.gerenciador_patrimonial.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de senha por login (login JSON do SPA e HTTP Basic da API).
 *
 * <p>Depois de {@code app.login.max-falhas} falhas dentro da janela, o login fica bloqueado
 * pelo mesmo intervalo; a contagem zera no primeiro sucesso. Vale também para logins que não
 * existem, para o bloqueio não revelar quais contas existem. Em memória: reinicia com a
 * aplicação, suficiente para uma instância única.</p>
 */
@Service
public class TentativasLoginService {

    private record Estado(int falhas, Instant inicio, Instant bloqueadoAte) {}

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final int maxFalhas;
    private final Duration janela;
    private final Clock clock;

    public TentativasLoginService(@Value("${app.login.max-falhas:10}") int maxFalhas,
                                  @Value("${app.login.janela-minutos:15}") long janelaMinutos) {
        this.maxFalhas = maxFalhas;
        this.janela = Duration.ofMinutes(janelaMinutos);
        this.clock = Clock.systemUTC();
    }

    public boolean bloqueado(String login) {
        Estado e = estados.get(chave(login));
        return e != null && e.bloqueadoAte() != null && e.bloqueadoAte().isAfter(clock.instant());
    }

    @EventListener
    public void falhou(AbstractAuthenticationFailureEvent evento) {
        String login = String.valueOf(evento.getAuthentication().getName());
        Instant agora = clock.instant();
        estados.compute(chave(login), (k, e) -> {
            if (e == null || e.inicio().plus(janela).isBefore(agora)) e = new Estado(0, agora, null);
            if (e.bloqueadoAte() != null && e.bloqueadoAte().isAfter(agora)) return e;
            int falhas = e.falhas() + 1;
            return new Estado(falhas, e.inicio(), falhas >= maxFalhas ? agora.plus(janela) : null);
        });
        // evita crescimento sem limite com logins aleatórios
        if (estados.size() > 10_000) estados.entrySet().removeIf(en -> en.getValue().inicio().plus(janela).isBefore(agora)
                && (en.getValue().bloqueadoAte() == null || en.getValue().bloqueadoAte().isBefore(agora)));
    }

    @EventListener
    public void sucesso(AuthenticationSuccessEvent evento) {
        estados.remove(chave(evento.getAuthentication().getName()));
    }

    private static String chave(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}

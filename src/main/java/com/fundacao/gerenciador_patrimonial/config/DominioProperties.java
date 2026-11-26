package com.fundacao.gerenciador_patrimonial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Login pela rede da Fundação: Active Directory (Samba AD) do domínio FPTO.LOCAL.
 *
 * <p>Com {@code habilitado=false} (padrão) nada muda: só existem as contas locais.
 * Ligado, quem tem conta no domínio entra com o mesmo usuário e senha do Windows; o perfil
 * sai dos grupos {@code <prefixoGrupo><Perfil>} criados em {@code ouGrupos}, que o
 * administrador gerencia pela tela "Grupos da rede" (o sistema grava direto no AD com a
 * conta de serviço).</p>
 *
 * @param habilitado                  liga o login pelo AD
 * @param urls                        servidores em ordem de preferência (failover), todos ldaps:// ou todos ldap://
 * @param dominio                     nome DNS do domínio (fpto.local); aceito como sufixo/prefixo no login
 * @param base                        DN base das buscas de usuários (DC=fpto,DC=local)
 * @param usuarioServico              conta que busca usuários e altera os grupos (UPN ou DN)
 * @param senhaServico                senha da conta de serviço
 * @param ouGrupos                    OU onde ficam os grupos do Patrimônio
 * @param prefixoGrupo                prefixo do nome dos grupos (Patrimonio- → Patrimonio-Fiscal)
 * @param certificadoCa               PEM da CA do AD (no Samba: /var/lib/samba/private/tls/ca.pem); vazio = truststore da JVM
 * @param confiarEmQualquerCertificado só para testes de bancada: aceita qualquer certificado TLS
 * @param timeoutMs                   tempo máximo de conexão e de resposta do AD
 */
@ConfigurationProperties(prefix = "app.ad")
public record DominioProperties(
        boolean habilitado,
        List<String> urls,
        String dominio,
        String base,
        String usuarioServico,
        String senhaServico,
        String ouGrupos,
        String prefixoGrupo,
        String certificadoCa,
        boolean confiarEmQualquerCertificado,
        Integer timeoutMs
) {
    public DominioProperties {
        urls = urls == null ? List.of() : urls.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        prefixoGrupo = prefixoGrupo == null || prefixoGrupo.isBlank() ? "Patrimonio-" : prefixoGrupo.trim();
        timeoutMs = timeoutMs == null || timeoutMs <= 0 ? 5000 : timeoutMs;
    }
}

package com.fundacao.gerenciador_patrimonial;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O APK do app Android é baixado sem login em /downloads/AppPatrimonio.apk, com o MIME do Android. */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class DownloadsTest {

    @Autowired MockMvc mvc;
    @Value("${app.storage.pasta-raiz}") String pastaRaiz;

    @Test
    void apkPublicoComTipoAndroid() throws Exception {
        Path apk = Path.of(pastaRaiz, "downloads", "AppPatrimonio.apk");
        Files.createDirectories(apk.getParent());
        Files.write(apk, "PK-teste".getBytes());

        mvc.perform(head("/downloads/AppPatrimonio.apk")).andExpect(status().isOk());
        mvc.perform(get("/downloads/AppPatrimonio.apk"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/vnd.android.package-archive")))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().bytes("PK-teste".getBytes()));
        // o botão usa ?v=<data de publicação> para furar caches; o arquivo é o mesmo
        mvc.perform(get("/downloads/AppPatrimonio.apk").param("v", "20261019-1636"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Last-Modified"))
                .andExpect(content().bytes("PK-teste".getBytes()));
    }

    @Test
    void arquivoInexistenteDa404() throws Exception {
        mvc.perform(get("/downloads/nao-existe.apk")).andExpect(status().isNotFound());
        mvc.perform(get("/downloads/../application.yml")).andExpect(status().is4xxClientError());
    }
}

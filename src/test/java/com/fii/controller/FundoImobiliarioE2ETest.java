package com.fii.controller;

import tools.jackson.databind.ObjectMapper;
import com.fii.dto.FundoImobiliarioRequestDTO;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.FundoImobiliarioRepository;
import com.fii.service.FiiDadosMercadoService;
import com.fii.service.FiiRecomendacoesService;
import com.fii.service.FiisEmAltaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FundoImobiliarioE2ETest {

    private static final String BASE_URL = "/api/v1/fundos-imobiliarios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FundoImobiliarioRepository repository;

    @MockitoBean
    private FiiDadosMercadoService dadosMercadoService;

    @MockitoBean
    private FiiRecomendacoesService recomendacoesService;

    @MockitoBean
    private FiisEmAltaService fiisEmAltaService;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    // ── POST → 201 Created ───────────────────────────────────────────────────

    @Test
    @Order(1)
    void deveCriar_comDadosValidos_retorna201() throws Exception {
        FundoImobiliarioRequestDTO dto = new FundoImobiliarioRequestDTO(
                "HGLG11", "CSHG Logística FII", "Fundo de Tijolo", "Logística"
        );

        mockMvc.perform(post(BASE_URL)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("HGLG11"))
                .andExpect(jsonPath("$.nome").value("CSHG Logística FII"))
                .andExpect(jsonPath("$.tipo").value("Fundo de Tijolo"))
                .andExpect(jsonPath("$.segmento").value("Logística"));
    }

    // ── POST → 400 (código duplicado) ────────────────────────────────────────

    @Test
    @Order(2)
    void deveCriar_comCodigoDuplicado_retorna400() throws Exception {
        repository.save(FundoImobiliario.builder()
                .codigo("HGLG11").nome("CSHG Logística FII")
                .tipo("Fundo de Tijolo").segmento("Logística").build());

        FundoImobiliarioRequestDTO dto = new FundoImobiliarioRequestDTO(
                "HGLG11", "Outro Fundo", "Fundo de Papel", "Recebíveis"
        );

        mockMvc.perform(post(BASE_URL)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── POST → 400 (campos inválidos) ────────────────────────────────────────

    @Test
    @Order(3)
    void deveCriar_comCamposVazios_retorna400() throws Exception {
        String payloadInvalido = "{\"codigo\":\"\",\"nome\":\"\",\"tipo\":\"\",\"segmento\":\"\"}";

        mockMvc.perform(post(BASE_URL)
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes").isArray());
    }

    // ── GET lista → 200 ──────────────────────────────────────────────────────

    @Test
    @Order(4)
    void deveListarTodos_retorna200ComLista() throws Exception {
        repository.save(FundoImobiliario.builder()
                .codigo("KNCR11").nome("Kinea Rendimentos Imobiliários FII")
                .tipo("Fundo de Papel").segmento("Recebíveis").build());
        repository.save(FundoImobiliario.builder()
                .codigo("MXRF11").nome("Maxi Renda FII")
                .tipo("Fundo de Papel").segmento("Recebíveis").build());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── GET por código → 200 ─────────────────────────────────────────────────

    @Test
    @Order(5)
    void deveBuscarPorCodigo_quandoExiste_retorna200() throws Exception {
        repository.save(FundoImobiliario.builder()
                .codigo("BTLG11").nome("BTG Pactual Logística FII")
                .tipo("Fundo de Tijolo").segmento("Logística").build());

        mockMvc.perform(get(BASE_URL + "/BTLG11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("BTLG11"));
    }

    // ── GET por código → 404 ─────────────────────────────────────────────────

    @Test
    @Order(6)
    void deveBuscarPorCodigo_quandoNaoExiste_retorna404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/XXXX11"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── PUT → 200 ────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void deveAtualizar_quandoExiste_retorna200() throws Exception {
        repository.save(FundoImobiliario.builder()
                .codigo("XPML11").nome("XP Malls FII")
                .tipo("Fundo de Tijolo").segmento("Shoppings").build());

        FundoImobiliarioRequestDTO dto = new FundoImobiliarioRequestDTO(
                "XPML11", "XP Malls FII - Atualizado", "Fundo de Tijolo", "Shoppings"
        );

        mockMvc.perform(put(BASE_URL + "/XPML11")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("XP Malls FII - Atualizado"));
    }

    // ── PUT → 404 ────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void deveAtualizar_quandoNaoExiste_retorna404() throws Exception {
        FundoImobiliarioRequestDTO dto = new FundoImobiliarioRequestDTO(
                "XXXX11", "Fundo Inexistente", "Fundo de Papel", "Recebíveis"
        );

        mockMvc.perform(put(BASE_URL + "/XXXX11")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── DELETE → 204 ─────────────────────────────────────────────────────────

    @Test
    @Order(9)
    void deveDeletar_quandoExiste_retorna204() throws Exception {
        repository.save(FundoImobiliario.builder()
                .codigo("VILG11").nome("Vinci Logística FII")
                .tipo("Fundo de Tijolo").segmento("Logística").build());

        mockMvc.perform(delete(BASE_URL + "/VILG11")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/VILG11"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE → 404 ─────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void deveDeletar_quandoNaoExiste_retorna404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/XXXX11")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}

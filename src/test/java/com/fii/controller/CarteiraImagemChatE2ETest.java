package com.fii.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.anthropic.services.blocking.MessageService;
import tools.jackson.databind.ObjectMapper;
import com.fii.dto.CarteiraChatRequestDTO;
import com.fii.entity.*;
import com.fii.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Cobre o fluxo de continuação de conversa de análise de carteira sem reenvio de imagem:
 * reconstrução de contexto a partir do banco (cache miss), persistência/uso do histórico
 * de mensagens e isolamento entre usuários.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarteiraImagemChatE2ETest {

    private static final String CONVERSA_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CarteiraImagemAnaliseRepository analiseRepository;

    @Autowired
    private CarteiraImagemDadosRepository imagemDadosRepository;

    @Autowired
    private CarteiraChatMensagemRepository mensagemRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private AnthropicClient anthropicClient;

    @MockitoBean
    private MessageService messageService;

    private Usuario usuarioDono;
    private Usuario outroUsuario;

    @BeforeEach
    void preparar() {
        mensagemRepository.deleteAll();
        imagemDadosRepository.deleteAll();
        analiseRepository.deleteAll();
        usuarioRepository.deleteAll();

        var cache = cacheManager.getCache("conversaCarteira");
        if (cache != null) cache.clear();

        usuarioDono = usuarioRepository.save(Usuario.builder()
                .nome("Dono da Carteira").email("dono-" + UUID.randomUUID() + "@teste.com")
                .senha("senha").role(UsuarioRole.USER).ativo(true).build());

        outroUsuario = usuarioRepository.save(Usuario.builder()
                .nome("Outro Usuário").email("outro-" + UUID.randomUUID() + "@teste.com")
                .senha("senha").role(UsuarioRole.USER).ativo(true).build());

        analiseRepository.save(CarteiraImagemAnalise.builder()
                .conversaId(CONVERSA_ID)
                .usuario(usuarioDono)
                .sentimentoGeral("POSITIVO")
                .resumo("Carteira diversificada com bons FIIs de tijolo e papel.")
                .pontosPositivos(List.of("Boa diversificação"))
                .pontosNegativos(List.of())
                .pontosAtencao(List.of())
                .ativosParaComprar(List.of())
                .ativosParaVender(List.of())
                .proximosAportes(List.of("HGLG11"))
                .recomendacaoGeral("Manter a estratégia atual.")
                .nomeArquivo("carteira.png")
                .analisadoEm(LocalDateTime.now())
                .build());

        imagemDadosRepository.save(CarteiraImagemDados.builder()
                .conversaId(CONVERSA_ID)
                .imagemBytes(new byte[] {1, 2, 3, 4})
                .mediaType("image/png")
                .build());

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(respostaFalsa("Resposta gerada pela IA sobre sua carteira."));
    }

    @Test
    void perguntar_quandoCacheMissEAnaliseEImagemPersistidas_reconstroiContextoSemImagem() throws Exception {
        CarteiraChatRequestDTO request = new CarteiraChatRequestDTO("Como está minha carteira?", null);

        mockMvc.perform(post("/api/v1/carteira/{conversaId}/perguntar", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(usuarioDono)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversaId").value(CONVERSA_ID))
                .andExpect(jsonPath("$.resposta").value("Resposta gerada pela IA sobre sua carteira."));

        List<CarteiraChatMensagem> mensagens = mensagemRepository.findByConversaIdOrderByCriadoEmAsc(CONVERSA_ID);
        org.assertj.core.api.Assertions.assertThat(mensagens).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(mensagens.get(0).getRole()).isEqualTo("USER");
        org.assertj.core.api.Assertions.assertThat(mensagens.get(1).getRole()).isEqualTo("ASSISTANT");
    }

    @Test
    void perguntar_emConversaJaIniciada_usaHistoricoPersistidoNaProximaPergunta() throws Exception {
        mensagemRepository.save(CarteiraChatMensagem.builder()
                .conversaId(CONVERSA_ID).role("USER").conteudo("Pergunta anterior").build());
        mensagemRepository.save(CarteiraChatMensagem.builder()
                .conversaId(CONVERSA_ID).role("ASSISTANT").conteudo("Resposta anterior").build());

        CarteiraChatRequestDTO request = new CarteiraChatRequestDTO("E agora, o que mudou?", null);

        mockMvc.perform(post("/api/v1/carteira/{conversaId}/perguntar", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(usuarioDono)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<CarteiraChatMensagem> mensagens = mensagemRepository.findByConversaIdOrderByCriadoEmAsc(CONVERSA_ID);
        org.assertj.core.api.Assertions.assertThat(mensagens).hasSize(4);
        org.assertj.core.api.Assertions.assertThat(mensagens.get(2).getConteudo()).isEqualTo("E agora, o que mudou?");
    }

    @Test
    void perguntar_quandoConversaPertenceAOutroUsuario_retorna404() throws Exception {
        CarteiraChatRequestDTO request = new CarteiraChatRequestDTO("Posso ver essa carteira?", null);

        mockMvc.perform(post("/api/v1/carteira/{conversaId}/perguntar", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(outroUsuario)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void perguntar_quandoConversaNaoExiste_retorna404() throws Exception {
        CarteiraChatRequestDTO request = new CarteiraChatRequestDTO("Existe essa conversa?", null);

        mockMvc.perform(post("/api/v1/carteira/{conversaId}/perguntar", "00000000-0000-0000-0000-000000000000")
                        .with(authentication(authenticacaoDe(usuarioDono)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void perguntar_comPerguntaVazia_retorna400() throws Exception {
        CarteiraChatRequestDTO request = new CarteiraChatRequestDTO("", null);

        mockMvc.perform(post("/api/v1/carteira/{conversaId}/perguntar", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(usuarioDono)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Consulta de histórico de mensagens via análise ───────────────────────

    @Test
    void buscarPorConversa_retornaMensagensJaTrocadas() throws Exception {
        mensagemRepository.save(CarteiraChatMensagem.builder()
                .conversaId(CONVERSA_ID).role("USER").conteudo("Pergunta 1").build());
        mensagemRepository.save(CarteiraChatMensagem.builder()
                .conversaId(CONVERSA_ID).role("ASSISTANT").conteudo("Resposta 1").build());

        mockMvc.perform(get("/api/v1/carteira/analise-imagem/{conversaId}", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(usuarioDono))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.mensagens[0].role").value("USER"))
                .andExpect(jsonPath("$.mensagens[0].conteudo").value("Pergunta 1"))
                .andExpect(jsonPath("$.mensagens[1].role").value("ASSISTANT"));
    }

    @Test
    void buscarPorConversa_semMensagens_retornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/v1/carteira/analise-imagem/{conversaId}", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(usuarioDono))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void buscarPorConversa_deOutroUsuario_retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/carteira/analise-imagem/{conversaId}", CONVERSA_ID)
                        .with(authentication(authenticacaoDe(outroUsuario))))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken authenticacaoDe(Usuario usuario) {
        return new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
    }

    private Message respostaFalsa(String texto) {
        return Message.builder()
                .id("msg_teste")
                .role(JsonValue.from("assistant"))
                .type(JsonValue.from("message"))
                .model(Model.CLAUDE_SONNET_4_6)
                .container(java.util.Optional.empty())
                .stopReason(StopReason.END_TURN)
                .stopSequence(java.util.Optional.empty())
                .content(List.of(ContentBlock.ofText(
                        TextBlock.builder()
                                .text(texto)
                                .citations(List.of())
                                .type(JsonValue.from("text"))
                                .build())))
                .usage(Usage.builder()
                        .inputTokens(10)
                        .outputTokens(10)
                        .cacheCreation(CacheCreation.builder()
                                .ephemeral1hInputTokens(0)
                                .ephemeral5mInputTokens(0)
                                .build())
                        .cacheCreationInputTokens(0L)
                        .cacheReadInputTokens(0L)
                        .inferenceGeo(java.util.Optional.empty())
                        .serverToolUse(java.util.Optional.empty())
                        .serviceTier(java.util.Optional.empty())
                        .build())
                .build();
    }
}

package com.fii.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.*;
import com.fii.dto.CarteiraChatRequestDTO;
import com.fii.dto.CarteiraChatResponseDTO;
import com.fii.dto.MensagemHistoricoDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraChatService {

    private final AnthropicClient anthropicClient;
    private final CacheManager cacheManager;

    private static final String MODEL = "claude-sonnet-4-6";

    private static final String SYSTEM_PROMPT_BASE = """
            Você é um analista de investimentos especializado no mercado brasileiro, com expertise em FIIs, ações, BDRs, ações estrangeiras, Tesouro Direto, ETFs, criptoativos e outros ativos.

            O usuário está fazendo perguntas sobre a sua carteira de investimentos. A análise completa da carteira foi realizada anteriormente a partir da imagem enviada pelo usuário e está disponível abaixo como referência.

            DIRETRIZES PARA RESPOSTAS:
            - Use a análise prévia e a imagem da carteira como base para responder
            - Seja direto, claro e objetivo
            - Cite os tickers/códigos dos ativos ao fazer referência a eles (ex: HGLG11, PETR4, IVVB11, MSFT34)
            - Indique o tipo do ativo quando relevante (FII, ação, BDR, Tesouro Direto, etc.)
            - Fundamente sugestões com dados da análise quando possível
            - Para perguntas fora do escopo da carteira, responda de forma breve e redirecione o foco
            - Nunca invente dados que não estejam na imagem ou na análise prévia

            Responda sempre em português brasileiro.

            ═══════════════════════════════════════
            ANÁLISE PRÉVIA DA CARTEIRA:
            ═══════════════════════════════════════
            """;

    public CarteiraChatResponseDTO perguntar(String conversaId, CarteiraChatRequestDTO request) {
        ConversaContexto contexto = recuperarContexto(conversaId);

        log.info("Chat carteira conversaId={} — pergunta: {}", conversaId,
                request.pergunta().length() > 80
                        ? request.pergunta().substring(0, 80) + "..." : request.pergunta());

        try {
            String systemPrompt = SYSTEM_PROMPT_BASE + contexto.analiseJson();

            // Monta o bloco de imagem a partir do cache
            Base64ImageSource imageSource = Base64ImageSource.builder()
                    .mediaType(contexto.mediaType())
                    .data(contexto.base64Imagem())
                    .build();

            ImageBlockParam imageBlock = ImageBlockParam.builder()
                    .source(ImageBlockParam.Source.ofBase64(imageSource))
                    .build();

            var builder = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(2048L)
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder()
                                    .text(systemPrompt)
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build()
                    ))
                    // Primeira mensagem sintética: usuário envia a imagem
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(imageBlock),
                            ContentBlockParam.ofText(
                                    TextBlockParam.builder()
                                            .text("Esta é minha carteira de investimentos. Use a imagem e a análise prévia para responder minhas perguntas.")
                                            .build()
                            )
                    ))
                    // Resposta sintética do assistente reconhecendo a análise
                    .addAssistantMessage("Certo! Já analisei sua carteira. Pode fazer suas perguntas.");

            // Adiciona o histórico da conversa (pares user/assistant)
            List<MensagemHistoricoDTO> historico = request.historico() != null
                    ? request.historico() : List.of();

            for (MensagemHistoricoDTO msg : historico) {
                if ("user".equalsIgnoreCase(msg.role())) {
                    builder.addUserMessage(msg.conteudo());
                } else {
                    builder.addAssistantMessage(msg.conteudo());
                }
            }

            // Adiciona a pergunta atual
            builder.addUserMessage(request.pergunta());

            Message response = anthropicClient.messages().create(builder.build());

            String resposta = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Resposta vazia da API"));

            log.info("Resposta gerada para conversaId={}. Cache hit: {}",
                    conversaId,
                    response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

            return new CarteiraChatResponseDTO(conversaId, request.pergunta(), resposta, LocalDateTime.now());

        } catch (UnauthorizedException e) {
            log.error("API key da Anthropic inválida.");
            throw new IllegalStateException("Análise por IA indisponível: API key não configurada.");
        } catch (AnthropicException e) {
            log.error("Erro na API Anthropic (chat conversaId={}): {} — {}",
                    conversaId, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Erro na API de IA: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro no chat de carteira (conversaId={}): {}", conversaId, e.getMessage(), e);
            throw new RuntimeException("Erro ao processar pergunta: " + e.getMessage());
        }
    }

    private ConversaContexto recuperarContexto(String conversaId) {
        Cache cache = cacheManager.getCache("conversaCarteira");
        if (cache == null) {
            throw new RuntimeException("Cache de conversas não configurado.");
        }
        ConversaContexto contexto = cache.get(conversaId, ConversaContexto.class);
        if (contexto == null) {
            throw new EntityNotFoundException(
                    "Conversa não encontrada ou expirada. Realize uma nova análise de imagem para iniciar uma conversa.");
        }
        return contexto;
    }
}
package com.fii.controller;

import com.fii.dto.*;
import com.fii.entity.CarteiraImagemAnalise;
import com.fii.entity.Usuario;
import com.fii.repository.CarteiraImagemAnaliseRepository;
import com.fii.service.CarteiraChatService;
import com.fii.service.CarteiraImagemAnaliseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carteira")
@RequiredArgsConstructor
public class CarteiraImagemController {

    private final CarteiraImagemAnaliseService analiseService;
    private final CarteiraChatService chatService;
    private final CarteiraImagemAnaliseRepository analiseRepository;

    @PostMapping(value = "/analise-imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseIniciadaDTO> analisarPorImagem(
            @RequestParam("imagens") List<MultipartFile> imagens,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.accepted().body(analiseService.iniciarAnalise(imagens, usuario));
    }

    @GetMapping("/analise-imagem/status/{jobId}")
    public ResponseEntity<AnaliseStatusDTO> consultarStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(analiseService.consultarStatus(jobId));
    }

    @GetMapping("/analise-imagem/historico")
    public ResponseEntity<List<CarteiraImagemAnaliseDTO>> historico(
            @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) return ResponseEntity.ok(List.of());
        List<CarteiraImagemAnaliseDTO> dtos = analiseRepository
                .findByUsuarioOrderByAnalisadoEmDesc(usuario)
                .stream()
                .map(a -> toDTO(a, List.of()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/analise-imagem/{conversaId}")
    public ResponseEntity<CarteiraImagemAnaliseDTO> buscarPorConversa(
            @PathVariable String conversaId,
            @AuthenticationPrincipal Usuario usuario) {
        CarteiraImagemAnalise analise = analiseRepository
                .findByUsuarioOrderByAnalisadoEmDesc(usuario)
                .stream()
                .filter(a -> a.getConversaId().equals(conversaId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Análise não encontrada: " + conversaId));
        return ResponseEntity.ok(toDTO(analise, chatService.listarMensagens(conversaId)));
    }

    @PostMapping("/{conversaId}/perguntar")
    public ResponseEntity<CarteiraChatResponseDTO> perguntar(
            @PathVariable String conversaId,
            @Valid @RequestBody CarteiraChatRequestDTO request,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(chatService.perguntar(conversaId, request, usuario));
    }

    private CarteiraImagemAnaliseDTO toDTO(CarteiraImagemAnalise a, List<CarteiraChatMensagemDTO> mensagens) {
        return new CarteiraImagemAnaliseDTO(
                a.getConversaId(),
                a.getSentimentoGeral(),
                a.getResumo(),
                a.getPontosPositivos() != null ? a.getPontosPositivos() : List.of(),
                a.getPontosNegativos() != null ? a.getPontosNegativos() : List.of(),
                a.getPontosAtencao() != null ? a.getPontosAtencao() : List.of(),
                a.getAtivosParaComprar() != null ? a.getAtivosParaComprar() : List.of(),
                a.getAtivosParaVender() != null ? a.getAtivosParaVender() : List.of(),
                a.getProximosAportes() != null ? a.getProximosAportes() : List.of(),
                a.getRecomendacaoGeral(),
                a.getAnalisadoEm(),
                mensagens
        );
    }
}

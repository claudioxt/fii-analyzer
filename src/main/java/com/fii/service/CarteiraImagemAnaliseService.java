package com.fii.service;

import com.anthropic.models.messages.Base64ImageSource;
import com.fii.dto.AnaliseIniciadaDTO;
import com.fii.dto.AnaliseStatusDTO;
import com.fii.entity.Usuario;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraImagemAnaliseService {

    private final CarteiraImagemProcessadorService processador;
    private final AnaliseJobStore jobStore;

    private static final Map<String, Base64ImageSource.MediaType> MEDIA_TYPES = Map.of(
            "image/jpeg", Base64ImageSource.MediaType.IMAGE_JPEG,
            "image/jpg",  Base64ImageSource.MediaType.IMAGE_JPEG,
            "image/png",  Base64ImageSource.MediaType.IMAGE_PNG,
            "image/gif",  Base64ImageSource.MediaType.IMAGE_GIF,
            "image/webp", Base64ImageSource.MediaType.IMAGE_WEBP
    );

    public AnaliseIniciadaDTO iniciarAnalise(List<MultipartFile> imagens, Usuario usuario) {
        if (imagens == null || imagens.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma imagem fornecida.");
        }
        if (imagens.size() > 5) {
            throw new IllegalArgumentException("Máximo de 5 imagens por análise.");
        }
        try {
            List<ImagemDados> dados = new ArrayList<>();
            for (MultipartFile imagem : imagens) {
                validarImagem(imagem);
                dados.add(new ImagemDados(
                        imagem.getBytes(),
                        resolverMediaType(imagem.getContentType()),
                        imagem.getOriginalFilename()
                ));
            }

            String jobId = UUID.randomUUID().toString();
            jobStore.iniciar(jobId);
            processador.processar(jobId, dados, usuario);

            log.info("Análise iniciada. jobId={}, imagens={}", jobId, imagens.size());
            return new AnaliseIniciadaDTO(jobId, "PROCESSANDO");
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler imagem: " + e.getMessage());
        }
    }

    public AnaliseStatusDTO consultarStatus(String jobId) {
        return jobStore.buscar(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Análise não encontrada ou expirada. Realize uma nova análise para iniciar."));
    }

    private void validarImagem(MultipartFile imagem) {
        if (imagem == null || imagem.isEmpty()) {
            throw new IllegalArgumentException("Imagem não fornecida ou vazia.");
        }
        String contentType = imagem.getContentType();
        if (contentType == null || !MEDIA_TYPES.containsKey(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Formato de imagem não suportado: " + contentType +
                    ". Formatos aceitos: JPEG, PNG, GIF, WEBP.");
        }
        if (imagem.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Imagem excede o tamanho máximo de 5 MB.");
        }
    }

    private Base64ImageSource.MediaType resolverMediaType(String contentType) {
        if (contentType == null) return Base64ImageSource.MediaType.IMAGE_JPEG;
        return MEDIA_TYPES.getOrDefault(contentType.toLowerCase(), Base64ImageSource.MediaType.IMAGE_JPEG);
    }
}

package com.fii.controller;

import com.fii.dto.CarteiraAnaliseIADTO;
import com.fii.dto.CarteiraRecomendadaRequestDTO;
import com.fii.dto.CarteiraRecomendadaResponseDTO;
import com.fii.service.CarteiraAnaliseIAService;
import com.fii.service.CarteiraGerarIAService;
import com.fii.service.CarteiraRecomendadaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/carteiras-recomendadas")
@RequiredArgsConstructor
public class CarteiraRecomendadaController {

    private final CarteiraRecomendadaService service;
    private final CarteiraAnaliseIAService analiseIAService;
    private final CarteiraGerarIAService gerarIAService;

    @GetMapping
    public ResponseEntity<List<CarteiraRecomendadaResponseDTO>> listarTodas(
            @RequestParam(required = false) String casaDeAnalise,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mesReferencia) {
        return ResponseEntity.ok(service.listarTodas(casaDeAnalise, mesReferencia));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarteiraRecomendadaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CarteiraRecomendadaResponseDTO> criar(
            @Valid @RequestBody CarteiraRecomendadaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarteiraRecomendadaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody CarteiraRecomendadaRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analise-ia")
    public ResponseEntity<CarteiraAnaliseIADTO> analisarIA(@PathVariable Long id) {
        return ResponseEntity.ok(analiseIAService.analisar(id));
    }

    @PostMapping("/gerar-ia")
    public ResponseEntity<CarteiraRecomendadaResponseDTO> gerarComIA() {
        return ResponseEntity.status(HttpStatus.CREATED).body(gerarIAService.gerarCarteira());
    }
}
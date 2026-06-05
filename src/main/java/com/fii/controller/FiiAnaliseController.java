package com.fii.controller;

import com.fii.dto.FiiAnaliseCompletaDTO;
import com.fii.dto.FiiAnaliseIADTO;
import com.fii.dto.FiisEmAltaDTO;
import com.fii.service.FiiAnaliseCompletaService;
import com.fii.service.FiiAnaliseIAService;
import com.fii.service.FiisEmAltaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fundos-imobiliarios")
@RequiredArgsConstructor
public class FiiAnaliseController {

    private final FiiAnaliseCompletaService analiseService;
    private final FiiAnaliseIAService analiseIAService;
    private final FiisEmAltaService fiisEmAltaService;

    @GetMapping("/{codigo}/analise")
    public ResponseEntity<FiiAnaliseCompletaDTO> analisarFii(@PathVariable String codigo) {
        return ResponseEntity.ok(analiseService.analisar(codigo));
    }

    @GetMapping("/{codigo}/analise-ia")
    public ResponseEntity<FiiAnaliseIADTO> analisarFiiComIA(@PathVariable String codigo) {
        return ResponseEntity.ok(analiseIAService.analisar(codigo));
    }

    @GetMapping("/em-alta")
    public ResponseEntity<FiisEmAltaDTO> fiisEmAlta() {
        return ResponseEntity.ok(fiisEmAltaService.buscar());
    }
}
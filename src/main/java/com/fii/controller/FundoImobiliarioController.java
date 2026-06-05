package com.fii.controller;

import com.fii.dto.FundoImobiliarioRequestDTO;
import com.fii.dto.FundoImobiliarioResponseDTO;
import com.fii.service.FundoImobiliarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fundos-imobiliarios")
@RequiredArgsConstructor
public class FundoImobiliarioController {

    private final FundoImobiliarioService service;

    @GetMapping
    public ResponseEntity<List<FundoImobiliarioResponseDTO>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<FundoImobiliarioResponseDTO> buscarPorCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(service.buscarPorCodigo(codigo));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FundoImobiliarioResponseDTO> criar(@Valid @RequestBody FundoImobiliarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FundoImobiliarioResponseDTO> atualizar(
            @PathVariable String codigo,
            @Valid @RequestBody FundoImobiliarioRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(codigo, dto));
    }

    @DeleteMapping("/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletar(@PathVariable String codigo) {
        service.deletar(codigo);
        return ResponseEntity.noContent().build();
    }
}

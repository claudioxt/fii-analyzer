package com.fii.service;

import com.fii.dto.AnaliseStatusDTO;
import com.fii.dto.CarteiraImagemAnaliseDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnaliseJobStore {

    private final Map<String, AnaliseStatusDTO> jobs = new ConcurrentHashMap<>();

    public void iniciar(String jobId) {
        jobs.put(jobId, new AnaliseStatusDTO(jobId, "PROCESSANDO", null, null));
    }

    public void concluir(String jobId, CarteiraImagemAnaliseDTO resultado) {
        jobs.put(jobId, new AnaliseStatusDTO(jobId, "CONCLUIDO", resultado, null));
    }

    public void falhar(String jobId, String erro) {
        jobs.put(jobId, new AnaliseStatusDTO(jobId, "ERRO", null, erro));
    }

    public Optional<AnaliseStatusDTO> buscar(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}

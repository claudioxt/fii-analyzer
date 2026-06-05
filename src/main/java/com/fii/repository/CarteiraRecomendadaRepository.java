package com.fii.repository;

import com.fii.entity.CarteiraRecomendada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarteiraRecomendadaRepository extends JpaRepository<CarteiraRecomendada, Long> {

    @Query("SELECT MAX(c.mesReferencia) FROM CarteiraRecomendada c")
    Optional<LocalDate> findMaxMesReferencia();

    List<CarteiraRecomendada> findAllByOrderByMesReferenciaDescCasaDeAnaliseAsc();

    List<CarteiraRecomendada> findByCasaDeAnaliseIgnoreCaseOrderByMesReferenciaDesc(String casaDeAnalise);

    List<CarteiraRecomendada> findByMesReferenciaOrderByCasaDeAnaliseAsc(LocalDate mesReferencia);
}
package com.fii.repository;

import com.fii.entity.FundoImobiliario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FundoImobiliarioRepository extends JpaRepository<FundoImobiliario, String> {

    List<FundoImobiliario> findBySegmento(String segmento);

    @Query("SELECT f FROM FundoImobiliario f WHERE LOWER(f.nome) LIKE LOWER(CONCAT('%', :termo, '%'))")
    List<FundoImobiliario> buscarPorNome(String termo);
}

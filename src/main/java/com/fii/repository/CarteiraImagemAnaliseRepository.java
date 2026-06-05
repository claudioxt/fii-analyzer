package com.fii.repository;

import com.fii.entity.CarteiraImagemAnalise;
import com.fii.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarteiraImagemAnaliseRepository extends JpaRepository<CarteiraImagemAnalise, Long> {

    List<CarteiraImagemAnalise> findTop3ByUsuarioOrderByAnalisadoEmDesc(Usuario usuario);

    List<CarteiraImagemAnalise> findByUsuarioOrderByAnalisadoEmDesc(Usuario usuario);
}

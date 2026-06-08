package com.fii.repository;

import com.fii.entity.CarteiraChatMensagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarteiraChatMensagemRepository extends JpaRepository<CarteiraChatMensagem, Long> {

    List<CarteiraChatMensagem> findByConversaIdOrderByCriadoEmAsc(String conversaId);

    List<CarteiraChatMensagem> findTop20ByConversaIdOrderByCriadoEmDesc(String conversaId);
}

package com.fii.service;

import com.anthropic.models.messages.Base64ImageSource;

record ConversaContexto(
        String base64Imagem,
        Base64ImageSource.MediaType mediaType,
        String analiseJson
) {}
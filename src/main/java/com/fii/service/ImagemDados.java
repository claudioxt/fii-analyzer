package com.fii.service;

import com.anthropic.models.messages.Base64ImageSource;

record ImagemDados(byte[] bytes, Base64ImageSource.MediaType mediaType, String nome) {}

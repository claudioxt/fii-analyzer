package com.fii.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fii.dto.SugestaoAtivoDTO;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ListSugestaoFiiConverter implements AttributeConverter<List<SugestaoAtivoDTO>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SugestaoAtivoDTO> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try { return MAPPER.writeValueAsString(attribute); }
        catch (Exception e) { return null; }
    }

    @Override
    public List<SugestaoAtivoDTO> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try { return MAPPER.readValue(dbData, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}

package com.codemind.platform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;

public final class JsonbHelper {

    private JsonbHelper() {
    }

    public static PGobject toJsonb(Object value, ObjectMapper objectMapper) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(objectMapper.writeValueAsString(value));
            return jsonObject;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert object to jsonb", e);
        }
    }
}
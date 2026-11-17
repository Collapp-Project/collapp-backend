package com.collapp.project.dto.error;

import java.time.Instant;
import java.util.Map;

/** Contrato único de error: {@code error} es un código estable y {@code message} el texto para el usuario. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {

    /** Clave para los errores de validación que no pertenecen a ningún campo. */
    public static final String FORM_ERROR_KEY = "_form";
}

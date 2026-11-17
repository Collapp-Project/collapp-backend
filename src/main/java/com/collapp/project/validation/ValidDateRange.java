package com.collapp.project.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Valida que dos campos LocalDate del record anotado estén en orden cronológico;
 * el error resultante se notifica bajo la clave {@code _form} (ver {@code ApiError.FORM_ERROR_KEY}).
 */
@Target(ElementType.TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = ValidDateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "La fecha de fin no puede ser anterior a la fecha de inicio";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Nombre del campo que marca el inicio del rango. */
    String start();

    /** Nombre del campo que marca el fin del rango. */
    String end();
}

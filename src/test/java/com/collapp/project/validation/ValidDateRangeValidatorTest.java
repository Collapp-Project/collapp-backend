package com.collapp.project.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ValidDateRangeValidatorTest {

    private ValidDateRangeValidator validator;
    private ConstraintValidatorContext context;

    /** Record mínimo de prueba: el validador solo conoce los nombres de los campos. */
    @ValidDateRange(start = "startDate", end = "endDate")
    private record DateRange(LocalDate startDate, LocalDate endDate) {}

    @BeforeEach
    void setUp() {
        validator = new ValidDateRangeValidator();
        validator.initialize(DateRange.class.getAnnotation(ValidDateRange.class));
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void valida_cuando_el_fin_es_posterior_al_inicio() {
        DateRange range = new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));

        assertTrue(validator.isValid(range, context));
    }

    @Test
    void valida_cuando_inicio_y_fin_coinciden() {
        DateRange range = new DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));

        assertTrue(validator.isValid(range, context));
    }

    @Test
    void rechaza_cuando_el_fin_es_anterior_al_inicio() {
        DateRange range = new DateRange(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 1));

        assertFalse(validator.isValid(range, context));
    }

    @Test
    void ignora_campos_nulos_porque_su_obligatoriedad_la_cubren_otras_anotaciones() {
        DateRange sinFin = new DateRange(LocalDate.of(2026, 8, 1), null);
        DateRange sinInicio = new DateRange(null, LocalDate.of(2026, 8, 15));

        assertTrue(validator.isValid(sinFin, context));
        assertTrue(validator.isValid(sinInicio, context));
    }

    @Test
    void acepta_el_objeto_nulo() {
        assertTrue(validator.isValid(null, context));
    }
}

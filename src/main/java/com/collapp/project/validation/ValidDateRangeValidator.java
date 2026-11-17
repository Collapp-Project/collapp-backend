package com.collapp.project.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;
import java.time.LocalDate;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startFieldName;
    private String endFieldName;

    @Override
    public void initialize(ValidDateRange annotation) {
        startFieldName = annotation.start();
        endFieldName = annotation.end();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate start = readField(value, startFieldName);
        LocalDate end = readField(value, endFieldName);

        // La presencia de cada campo la validan sus @NotNull; aquí solo se valida el orden.
        if (start == null || end == null) {
            return true;
        }

        return !end.isBefore(start);
    }

    private LocalDate readField(Object value, String fieldName) {
        try {
            Field field = value.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (LocalDate) field.get(value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException(
                    "El campo '" + fieldName + "' no existe o no es accesible en "
                            + value.getClass().getName(), ex);
        }
    }
}

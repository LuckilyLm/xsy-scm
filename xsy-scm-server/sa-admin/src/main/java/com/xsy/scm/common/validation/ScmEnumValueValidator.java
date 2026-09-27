package com.xsy.scm.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps String-backed request fields aligned with their existing enum vocabulary. */
public class ScmEnumValueValidator
        implements
            ConstraintValidator<
                    ScmEnumValue,
                    String> {

    private Set<
            String> enumNames;

    @Override
    public void initialize(ScmEnumValue constraintAnnotation) {
        enumNames = Arrays.stream(constraintAnnotation.enumClass().getEnumConstants()).map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || enumNames.contains(value);
    }
}

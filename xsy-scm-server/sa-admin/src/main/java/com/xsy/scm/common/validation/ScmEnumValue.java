package com.xsy.scm.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a String against enum constant names without changing the JSON field type.
 *
 * <p>
 * {@code null} passes this constraint so callers can combine it with {@code @NotNull} or {@code @NotBlank} and provide
 * a field-specific message for required values.
 */
@Documented
@Constraint(validatedBy = ScmEnumValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ScmEnumValue {

    String message() default "值不在允许范围内";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    Class<? extends Enum<?>> enumClass();
}

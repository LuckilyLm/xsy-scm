package com.xianshuyuan.scm.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface PasswordPolicy {
    String message() default "密码不符合安全策略";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

final class PasswordPolicyValidator implements jakarta.validation.ConstraintValidator<PasswordPolicy, String> {
    @Override public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
        return PasswordPolicies.valid(value);
    }
}

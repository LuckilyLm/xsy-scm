package com.xsy.scm.common.validation;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 复用正式表单约束保护服务入口，使导入、后台任务等内部调用也校验参数。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ScmServiceValidationAspect {
    private final Validator validator;

    /**
     * 在事务和业务写入前校验表单；领域事实对象仍由各领域命令校验。
     *
     * @param invocation
     *            非空的SCM服务调用；不修改参数和返回值，表单违规时抛出业务参数异常。
     * @return 原服务返回值；合法参数继续执行原调用。
     * @throws Throwable
     *             原调用的异常原样向上传播。
     */
    @Around("execution(public * com.xsy.scm..service..*(..))")
    public Object validate(ProceedingJoinPoint invocation) throws Throwable {
        for (Object argument : invocation.getArgs()) {
            if (argument == null || !argument.getClass().getPackageName().startsWith("com.xsy.scm.")
                || !argument.getClass().getPackageName().contains(".domain.form")) {
                continue;
            }
            var violations = validator.validate(argument);
            if (!violations.isEmpty()) {
                String message = violations.stream().map(v -> v.getMessage()).distinct().sorted()
                    .collect(Collectors.joining("，"));
                throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR, message);
            }
        }
        return invocation.proceed();
    }
}

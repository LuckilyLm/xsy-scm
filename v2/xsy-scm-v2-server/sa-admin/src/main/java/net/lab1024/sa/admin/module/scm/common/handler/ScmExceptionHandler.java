package net.lab1024.sa.admin.module.scm.common.handler;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScmExceptionHandler {
    @ExceptionHandler(ScmBusinessException.class)
    public ResponseDTO<Void> handle(ScmBusinessException exception) {
        return ResponseDTO.error(exception.getErrorCode());
    }
}

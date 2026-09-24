package net.lab1024.sa.admin.module.scm.common.handler;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.base.common.code.UserErrorCode;
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

    /**
     * 数据范围越权与功能权限不足对客户端表现一致（30005），调用方无法据此探测行是否存在。
     */
    @ExceptionHandler(ScmDataScopeException.class)
    public ResponseDTO<Void> handleDataScope(ScmDataScopeException exception) {
        return ResponseDTO.error(UserErrorCode.NO_PERMISSION);
    }
}

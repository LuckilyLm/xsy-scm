package com.xsy.scm.device.controller;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.device.support.ScaleDeviceResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 设备业务拒绝保持字符串协议；不能落入后台数字错误码响应。 */
@RestControllerAdvice(assignableTypes = ScaleDeviceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScaleDeviceExceptionHandler {
    @ExceptionHandler(ScmBusinessException.class)
    public ResponseEntity<ScaleDeviceResponse<Void>> business(ScmBusinessException exception) {
        String code = exception.getErrorCode() instanceof Enum<?> value ? value.name() : "DEVICE_REJECTED";
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ScaleDeviceResponse.error(code, exception.getErrorCode().getMsg()));
    }

    @ExceptionHandler(ScmDataScopeException.class)
    public ResponseEntity<ScaleDeviceResponse<Void>> forbidden(ScmDataScopeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ScaleDeviceResponse.error("NO_PERMISSION", "无权操作该任务或仓库"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ScaleDeviceResponse<Void>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ScaleDeviceResponse.error("INVALID_REQUEST", "请求参数不完整或格式错误"));
    }
}

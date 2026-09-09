package com.xianshuyuan.scm.common.exception;

import com.xianshuyuan.scm.common.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.error(errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().filter(error -> error.getDefaultMessage() != null)
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.VALIDATION_ERROR.message());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        String message = exception.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_ERROR.message());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidationException(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ErrorCode.VALIDATION_ERROR.message());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.code(), ErrorCode.VALIDATION_ERROR.message()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException exception) {
        LOGGER.warn("Database constraint rejected a request: {}", exception.getClass().getSimpleName());
        String detail = constraintDetail(exception);
        ErrorCode code = detail.contains("uk_supplier_code_active")
                ? com.xianshuyuan.scm.supplier.service.SupplierErrorCodes.SUPPLIER_CODE_CONFLICT
                : detail.contains("uk_warehouse_code_active")
                ? com.xianshuyuan.scm.supplier.service.SupplierErrorCodes.WAREHOUSE_CODE_CONFLICT
                : detail.contains("uk_supplier_sku_active")
                ? com.xianshuyuan.scm.supplier.service.SupplierErrorCodes.SUPPLIER_SKU_UNIQUE_CONFLICT
                : ErrorCode.DATA_CONFLICT;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.code(), code.message()));
    }

    private String constraintDetail(DataIntegrityViolationException exception) {
        StringBuilder detail = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                detail.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        Throwable mostSpecificCause = exception.getMostSpecificCause();
        if (mostSpecificCause != exception && mostSpecificCause.getMessage() != null) {
            detail.append(mostSpecificCause.getMessage());
        }
        return detail.toString();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unhandled request failure", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }
}

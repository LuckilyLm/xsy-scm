package com.xianshuyuan.scm.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.AuthErrorCodes;
import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(errorCode.code(), errorCode.message()));
    }

    static void writeLoginRequired(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        write(response, objectMapper, AuthErrorCodes.LOGIN_REQUIRED);
    }

    static void writePermissionDenied(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        write(response, objectMapper, AuthErrorCodes.PERMISSION_DENIED);
    }

    static void writeSuccess(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.success(null));
    }
}

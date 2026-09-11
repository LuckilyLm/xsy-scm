package com.xianshuyuan.scm.mall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

public final class MallSecurityErrorWriter {

    private MallSecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(errorCode.code(), errorCode.message()));
    }
}

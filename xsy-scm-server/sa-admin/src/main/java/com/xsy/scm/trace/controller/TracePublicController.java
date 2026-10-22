package com.xsy.scm.trace.controller;

import com.xsy.scm.trace.domain.vo.TracePublicVO;
import com.xsy.scm.trace.service.TracePublicService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDownloadVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户与监管扫码的公开入口。
 *
 * <p>
 * 免登录，凭据是标签上的 {@code public_token}。路径前缀在 {@code MvcConfig} 里排除了员工登录拦截器—— 扫码的人可能既不是员工也不是商城客户，没有可用的登录态。
 */
@RestController
@RequestMapping("/scm/trace/public")
@Tag(name = "SCM 溯源公开查询")
@RequiredArgsConstructor
public class TracePublicController {

    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    private static final String HEADER_USER_AGENT = "User-Agent";

    private final TracePublicService tracePublicService;

    @GetMapping("/{publicToken}")
    public ResponseDTO<TracePublicVO> query(@PathVariable String publicToken, HttpServletRequest request) {
        return ResponseDTO.ok(tracePublicService.query(publicToken, clientIp(request), userAgent(request)));
    }

    @GetMapping("/{publicToken}/files")
    public void file(@PathVariable String publicToken, @RequestParam String fileKey, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        FileDownloadVO file = tracePublicService.file(publicToken, fileKey, clientIp(request));
        SmartResponseUtil.setDownloadFileHeader(response, file.getMetadata().getFileName(),
                file.getMetadata().getFileSize());
        response.getOutputStream().write(file.getData());
    }

    /** 反向代理会把真实来源写在 X-Forwarded-For 的第一个位置；直连时退回 remoteAddr。 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader(HEADER_USER_AGENT);
    }
}

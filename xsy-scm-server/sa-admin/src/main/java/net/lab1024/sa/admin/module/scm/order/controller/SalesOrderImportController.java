package net.lab1024.sa.admin.module.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderImportResultVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderImportService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order/import")
public class SalesOrderImportController {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /**
     * 模板固定在 sa-admin 的 classpath 下，随 JAR 一起发布，运行时不依赖开发机绝对路径。
     */
    private static final String TEMPLATE_RESOURCE = "template/sales-order-import.xlsx";
    private static final String TEMPLATE_FILE_NAME = "销售订单导入模板.xlsx";

    private final SalesOrderImportService service;
    private final SecurityFileService securityFileService;

    @GetMapping("/template")
    @SaCheckPermission("scm:order:import")
    public void template(HttpServletResponse response) throws IOException {
        var template = new ClassPathResource(TEMPLATE_RESOURCE);
        if (!template.exists()) {
            // 不能静默返回空文件：让前端拿到 JSON 错误提示，而不是把错误内容存成 .xlsx。
            SmartResponseUtil.write(response, ResponseDTO.userErrorParam("导入模板缺失，请重新部署应用后再试"));
            return;
        }
        // 一次性读入内存再写出：长度取实际字节数，避免 JAR 内资源二次定位，也保证 Content-Length 与正文一致。
        var content = template.getContentAsByteArray();
        SmartResponseUtil.setDownloadFileHeader(response, TEMPLATE_FILE_NAME, (long) content.length);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @PostMapping
    @SaCheckPermission("scm:order:import")
    @OperateLog
    public ResponseDTO<SalesOrderImportResultVO> importOrders(@RequestParam MultipartFile file,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) throws Exception {
        if (file.isEmpty()) return ResponseDTO.userErrorParam("导入文件不能为空");
        var name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            return ResponseDTO.userErrorParam("仅支持 .xlsx 文件");
        if (file.getSize() > MAX_FILE_SIZE) return ResponseDTO.userErrorParam("导入文件不能超过 5 MiB");
        var security = securityFileService.checkFile(file);
        if (!security.getOk()) return ResponseDTO.error(security);
        return ResponseDTO.ok(service.importFile(file, key, StpUtil.hasPermission("scm:order:price-override")));
    }
}

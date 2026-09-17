package com.xsy.scm.admin.module.business.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceWarnVO;
import com.xsy.scm.admin.module.business.trace.service.TraceWarnService;
import com.xsy.scm.base.common.domain.ResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 溯源预警 Controller（只读）
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "溯源管理-预警")
public class TraceWarnController {

    @Resource
    private TraceWarnService traceWarnService;

    @Operation(summary = "厂商资质到期预警 @author xsy-scm")
    @GetMapping("/trace/warn/manufacturer")
    @SaCheckPermission("traceWarn:query")
    public ResponseDTO<List<TraceWarnVO>> manufacturerWarn(@RequestParam(required = false) Integer warnDays) {
        return traceWarnService.manufacturerWarn(warnDays);
    }
}

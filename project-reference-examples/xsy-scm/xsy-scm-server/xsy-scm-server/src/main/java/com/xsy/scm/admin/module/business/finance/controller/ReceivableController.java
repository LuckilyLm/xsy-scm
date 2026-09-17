package com.xsy.scm.admin.module.business.finance.controller;

import com.xsy.scm.admin.module.business.finance.domain.form.ReceivableQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ReceivableVO;
import com.xsy.scm.admin.module.business.finance.service.ReceivableService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * 应收单 Controller
 *
 * @author xsy-scm
 */
@RestController
@RequestMapping("/finance/receivable")
@Tag(name = "财务管理-应收单")
public class ReceivableController {

    @Resource
    private ReceivableService receivableService;

    @Operation(summary = "分页查询应收单 @author xsy-scm")
    @PostMapping("/query")
    @SaCheckPermission("finance:receivable:query")
    public ResponseDTO<PageResult<ReceivableVO>> query(@RequestBody ReceivableQueryForm queryForm) {
        return receivableService.query(queryForm);
    }
}

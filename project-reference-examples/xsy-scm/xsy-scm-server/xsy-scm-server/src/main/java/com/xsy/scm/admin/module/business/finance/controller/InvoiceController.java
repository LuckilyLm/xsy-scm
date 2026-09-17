package com.xsy.scm.admin.module.business.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.finance.domain.form.InvoiceAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.InvoiceQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.InvoiceVO;
import com.xsy.scm.admin.module.business.finance.service.InvoiceService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发票 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "财务管理-发票")
public class InvoiceController {

    @Resource
    private InvoiceService invoiceService;

    @Operation(summary = "分页查询发票 @author xsy-scm")
    @PostMapping("/finance/invoice/query")
    @SaCheckPermission("invoice:query")
    public ResponseDTO<PageResult<InvoiceVO>> query(@RequestBody @Valid InvoiceQueryForm queryForm) {
        return invoiceService.query(queryForm);
    }

    @Operation(summary = "登记开票 @author xsy-scm")
    @PostMapping("/finance/invoice/add")
    @SaCheckPermission("invoice:add")
    public ResponseDTO<String> add(@RequestBody @Valid InvoiceAddForm addForm) {
        return invoiceService.add(addForm);
    }

    @Operation(summary = "发票红冲（同订单整单联动） @author xsy-scm")
    @PostMapping("/finance/invoice/redFlush/{invoiceId}")
    @SaCheckPermission("invoice:redFlush")
    public ResponseDTO<String> redFlush(@PathVariable Long invoiceId) {
        return invoiceService.redFlush(invoiceId);
    }

    @Operation(summary = "删除发票 @author xsy-scm")
    @GetMapping("/finance/invoice/delete/{invoiceId}")
    @SaCheckPermission("invoice:delete")
    public ResponseDTO<String> delete(@PathVariable Long invoiceId) {
        return invoiceService.delete(invoiceId);
    }

    @Operation(summary = "批量删除发票 @author xsy-scm")
    @PostMapping("/finance/invoice/batchDelete")
    @SaCheckPermission("invoice:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return invoiceService.batchDelete(idList);
    }
}

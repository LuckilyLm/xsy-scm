package com.xsy.scm.admin.module.business.finance.controller;

import com.xsy.scm.admin.module.business.finance.domain.form.PaymentAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.PaymentQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.PaymentVO;
import com.xsy.scm.admin.module.business.finance.service.PaymentService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * 收款单 Controller
 *
 * @author xsy-scm
 */
@RestController
@RequestMapping("/finance/payment")
@Tag(name = "财务管理-收款单")
public class PaymentController {

    @Resource
    private PaymentService paymentService;

    @Operation(summary = "登记收款单 @author xsy-scm")
    @PostMapping
    @SaCheckPermission("finance:payment:add")
    public ResponseDTO<String> add(@RequestBody PaymentAddForm form) {
        return paymentService.add(form);
    }

    @Operation(summary = "确认收款并核销应收 @author xsy-scm")
    @PostMapping("/confirm/{paymentId}")
    @SaCheckPermission("finance:payment:confirm")
    public ResponseDTO<String> confirm(@PathVariable Long paymentId) {
        return paymentService.confirm(paymentId);
    }

    @Operation(summary = "分页查询收款单 @author xsy-scm")
    @PostMapping("/query")
    @SaCheckPermission("finance:payment:query")
    public ResponseDTO<PageResult<PaymentVO>> query(@RequestBody PaymentQueryForm queryForm) {
        return paymentService.query(queryForm);
    }
}

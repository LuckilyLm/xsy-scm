package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务收款登记入口。
 *
 * <p>
 * 收款查询、导出与反向动作由独立命令提供，不复用本登记端点的权限。
 *
 * <p>
 * 收款是资金动作，重复请求会重复入账，所以必须带 {@code Idempotency-Key}： 同键同内容重放首次结果，不产生第二张收款单。
 */
@RestController
@RequestMapping("/scm/finance/receipt")
@Tag(name = "SCM 财务收款")
@RequiredArgsConstructor
public class FinanceReceiptController {

    private final FinanceReceiptService financeReceiptService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.RECEIPT_ADD)
    @OperateLog
    public ResponseDTO<
            FinanceReceiptVO> add(@Valid @RequestBody FinanceReceiptAddForm form,
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeReceiptService.add(form, idempotencyKey));
    }
}

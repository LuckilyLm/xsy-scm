package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/finance/customer-expense")
@RequiredArgsConstructor
public class FinanceCustomerExpenseController {
    private final FinanceCustomerExpenseService expenseService;

    @PostMapping("/query")
    @SaCheckPermission(FinancePermission.EXPENSE_QUERY)
    public ResponseDTO<PageResult<FinanceCustomerExpenseVO>> query(
        @Valid @RequestBody FinanceCustomerExpenseForm.Query form) {
        return ResponseDTO.ok(expenseService.query(form));
    }

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.EXPENSE_ADD)
    @OperateLog
    public ResponseDTO<FinanceCustomerExpenseVO> add(@Valid @RequestBody FinanceCustomerExpenseForm.Add form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(expenseService.add(form, key));
    }

    @PostMapping("/void")
    @SaCheckPermission(FinancePermission.EXPENSE_VOID)
    @OperateLog
    public ResponseDTO<FinanceCustomerExpenseVO> voidExpense(@Valid @RequestBody FinanceCustomerExpenseForm.Void form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(expenseService.voidExpense(form, key));
    }
}

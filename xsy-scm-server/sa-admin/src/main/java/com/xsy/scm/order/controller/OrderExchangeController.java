package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.domain.form.OrderExchangeCreateForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/order/exchange")
@RequiredArgsConstructor
public class OrderExchangeController {
    private final OrderExchangeService exchangeService;

    @GetMapping("/replacement/{returnId}")
    @SaCheckPermission(OrderPermission.RETURN_QUERY)
    public ResponseDTO<Long> replacement(@PathVariable Long returnId) {
        return ResponseDTO.ok(exchangeService.replacementOrderId(returnId));
    }

    @PostMapping("/create")
    @SaCheckPermission(value = {OrderPermission.ADD, OrderPermission.RETURN_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<SalesOrderDetailVO> create(@Valid @RequestBody OrderExchangeCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(exchangeService.create(form, key));
    }
}

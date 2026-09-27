package com.xsy.scm.order.controller;

import com.xsy.scm.order.service.OrderRefundService;

import com.xsy.scm.order.domain.form.OrderRefundCompleteForm;
import com.xsy.scm.order.domain.form.OrderRefundQueryForm;

import com.xsy.scm.order.domain.vo.OrderRefundVO;

import com.xsy.scm.order.permission.OrderPermission;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order/refund")
public class OrderRefundController {
    private final OrderRefundService orderRefundService;

    @PostMapping("/query")
    @SaCheckPermission(OrderPermission.REFUND_QUERY)
    public ResponseDTO<
            PageResult<
                    OrderRefundVO>> query(@Valid @RequestBody OrderRefundQueryForm orderRefundQueryForm) {
        return ResponseDTO.ok(orderRefundService.query(orderRefundQueryForm));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(OrderPermission.REFUND_QUERY)
    public ResponseDTO<
            OrderRefundVO> detail(@PathVariable Long refundId) {
        return ResponseDTO.ok(orderRefundService.detail(refundId));
    }

    @PostMapping("/complete")
    @SaCheckPermission(OrderPermission.REFUND_COMPLETE)
    @OperateLog
    public ResponseDTO<
            OrderRefundVO> complete(@Valid @RequestBody OrderRefundCompleteForm refundCompleteForm,
                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderRefundService.complete(refundCompleteForm, key));
    }

}

package com.xsy.scm.order.controller;

import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;

import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnQueryForm;

import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.OrderReturnVO;

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
@RequestMapping("/scm/order/return")
public class OrderReturnController {
    private final OrderReturnService orderReturnService;
    private final OrderReturnReceiptService orderReturnReceiptService;

    @PostMapping("/query")
    @SaCheckPermission(OrderPermission.RETURN_QUERY)
    public ResponseDTO<PageResult<OrderReturnVO>> query(@Valid @RequestBody OrderReturnQueryForm orderReturnQueryForm) {
        return ResponseDTO.ok(orderReturnService.query(orderReturnQueryForm));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(OrderPermission.RETURN_QUERY)
    public ResponseDTO<OrderReturnDetailVO> detail(@PathVariable("id") Long orderReturnId) {
        return ResponseDTO.ok(orderReturnService.detail(orderReturnId));
    }

    @PostMapping("/create")
    @SaCheckPermission(OrderPermission.RETURN_ADD)
    @OperateLog
    public ResponseDTO<OrderReturnDetailVO> create(@Valid @RequestBody OrderReturnAddForm orderReturnAddForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderReturnService.create(orderReturnAddForm, key));
    }

    @PostMapping("/approve")
    @SaCheckPermission(OrderPermission.RETURN_APPROVE)
    @OperateLog
    public ResponseDTO<OrderReturnDetailVO> approve(@Valid @RequestBody OrderReturnApproveForm orderReturnApproveForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderReturnService.approve(orderReturnApproveForm, key));
    }

    @PostMapping("/receive")
    @SaCheckPermission(OrderPermission.RETURN_RECEIVE)
    @OperateLog
    public ResponseDTO<OrderReturnReceiptVO> receive(@Valid @RequestBody OrderReturnReceiveForm form,
            @RequestHeader(value = "Idempotency-Key") String key) {
        return ResponseDTO.ok(orderReturnReceiptService.receive(form, key));
    }

    @PostMapping("/reject")
    @SaCheckPermission(OrderPermission.RETURN_REJECT)
    @OperateLog
    public ResponseDTO<OrderReturnDetailVO> reject(@Valid @RequestBody OrderReturnDecisionForm orderReturnDecisionForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderReturnService.reject(orderReturnDecisionForm, key));
    }

    @PostMapping("/cancel")
    @SaCheckPermission(OrderPermission.RETURN_CANCEL)
    @OperateLog
    public ResponseDTO<OrderReturnDetailVO> cancel(@Valid @RequestBody OrderReturnDecisionForm orderReturnDecisionForm,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderReturnService.cancel(orderReturnDecisionForm, key));
    }

}

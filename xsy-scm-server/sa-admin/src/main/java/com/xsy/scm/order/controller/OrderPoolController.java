package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.order.domain.form.OrderPoolAssignForm;
import com.xsy.scm.order.domain.form.OrderPoolClaimForm;
import com.xsy.scm.order.domain.form.OrderPoolQueryForm;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.domain.vo.OrderPoolAssigneeVO;

import java.util.List;

import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderPoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/order/pool")
@RequiredArgsConstructor
public class OrderPoolController {
    private final OrderPoolService orderPoolService;

    @GetMapping("/assignees")
    @SaCheckPermission(OrderPermission.ASSIGN)
    public ResponseDTO<List<OrderPoolAssigneeVO>> assignees() {
        return ResponseDTO.ok(orderPoolService.assignees());
    }

    @PostMapping("/query")
    @SaCheckPermission(OrderPermission.ASSIGN)
    public ResponseDTO<PageResult<SalesOrderVO>> query(@Valid @RequestBody OrderPoolQueryForm form) {
        return ResponseDTO.ok(orderPoolService.unassigned(form));
    }

    @PostMapping("/claim")
    @SaCheckPermission(OrderPermission.ASSIGN)
    @OperateLog
    public ResponseDTO<SalesOrderVO> claim(@Valid @RequestBody OrderPoolClaimForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderPoolService.claim(form, key));
    }

    @PostMapping("/assign")
    @SaCheckPermission(OrderPermission.ASSIGN)
    @OperateLog
    public ResponseDTO<SalesOrderVO> assign(@Valid @RequestBody OrderPoolAssignForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(orderPoolService.assign(form, key));
    }
}

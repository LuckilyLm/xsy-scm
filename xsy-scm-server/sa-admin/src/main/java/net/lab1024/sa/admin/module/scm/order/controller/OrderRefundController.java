package net.lab1024.sa.admin.module.scm.order.controller;

import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order/refund")
public class OrderRefundController {
    private final OrderRefundService service;

    @PostMapping("/query")
    @SaCheckPermission("scm:order:refund:query")
    public ResponseDTO<PageResult<OrderRefundVO>> query(@Valid @RequestBody OrderRefundQueryForm f) {
        return ResponseDTO.ok(service.query(f));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:order:refund:query")
    public ResponseDTO<OrderRefundVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(service.detail(id));
    }

    @PostMapping("/complete")
    @SaCheckPermission("scm:order:refund:complete")
    @OperateLog
    public ResponseDTO<OrderRefundVO> complete(@Valid @RequestBody OrderRefundCompleteForm f, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.complete(f, key));
    }

}

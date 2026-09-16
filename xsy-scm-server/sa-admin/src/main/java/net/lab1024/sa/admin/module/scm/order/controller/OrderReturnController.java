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
@RestController @RequiredArgsConstructor @RequestMapping("/scm/order/return")
public class OrderReturnController {
    private final OrderReturnService service;
    @PostMapping("/query") @SaCheckPermission("scm:order:return:query")
    public ResponseDTO<PageResult<OrderReturnVO>> query(@Valid @RequestBody OrderReturnQueryForm f){return ResponseDTO.ok(service.query(f));}
    @GetMapping("/detail/{id}") @SaCheckPermission("scm:order:return:query")
    public ResponseDTO<OrderReturnDetailVO> detail(@PathVariable Long id){return ResponseDTO.ok(service.detail(id));}
    @PostMapping("/create") @SaCheckPermission("scm:order:return:add") @OperateLog
    public ResponseDTO<OrderReturnDetailVO> create(@Valid @RequestBody OrderReturnAddForm f,@RequestHeader(value="Idempotency-Key",required=false) String key){return ResponseDTO.ok(service.create(f,key));}
    @PostMapping("/approve") @SaCheckPermission("scm:order:return:approve") @OperateLog
    public ResponseDTO<OrderReturnDetailVO> approve(@Valid @RequestBody OrderReturnApproveForm f,@RequestHeader(value="Idempotency-Key",required=false) String key){return ResponseDTO.ok(service.approve(f,key));}
    @PostMapping("/reject") @SaCheckPermission("scm:order:return:reject") @OperateLog
    public ResponseDTO<OrderReturnDetailVO> reject(@Valid @RequestBody OrderReturnDecisionForm f,@RequestHeader(value="Idempotency-Key",required=false) String key){return ResponseDTO.ok(service.reject(f,key));}
    @PostMapping("/cancel") @SaCheckPermission("scm:order:return:cancel") @OperateLog
    public ResponseDTO<OrderReturnDetailVO> cancel(@Valid @RequestBody OrderReturnDecisionForm f,@RequestHeader(value="Idempotency-Key",required=false) String key){return ResponseDTO.ok(service.cancel(f,key));}

}

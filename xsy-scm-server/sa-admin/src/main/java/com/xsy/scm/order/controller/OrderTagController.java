package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.domain.form.OrderTagCreateForm;
import com.xsy.scm.order.domain.vo.OrderTagVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderTagService;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/order/tags")
public class OrderTagController {
    private final OrderTagService orderTagService;

    @GetMapping
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<List<OrderTagVO>> list() {
        return ResponseDTO.ok(orderTagService.list());
    }

    @PostMapping("/create")
    @SaCheckPermission(value = {OrderPermission.QUERY, OrderPermission.UPDATE}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody OrderTagCreateForm form) {
        return ResponseDTO.ok(orderTagService.create(form));
    }

    @PostMapping("/apply")
    @SaCheckPermission(value = {OrderPermission.QUERY, OrderPermission.UPDATE}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<String> apply(@Valid @RequestBody OrderTagAssignForm form) {
        orderTagService.apply(form);
        return ResponseDTO.ok();
    }
}

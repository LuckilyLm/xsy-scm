package com.xsy.scm.delivery.controller;

import com.xsy.scm.delivery.permission.DeliveryPermission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.xsy.scm.delivery.service.DeliveryDriverService;
import com.xsy.scm.delivery.domain.form.DeliveryDriverForm;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.entity.DeliveryDriverEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryDriverVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/delivery/drivers")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM配送司机")
public class DeliveryDriverController {
    private final DeliveryDriverService deliveryDriverService;

    @GetMapping
    @SaCheckPermission(DeliveryPermission.DRIVER_QUERY)
    public ResponseDTO<PageResult<DeliveryDriverVO>> query(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(deliveryDriverService.query(form));
    }

    @PostMapping
    @SaCheckPermission(DeliveryPermission.DRIVER_EDIT)
    @OperateLog
    public ResponseDTO<Long> save(@Valid @RequestBody DeliveryDriverForm form) {
        return ResponseDTO.ok(deliveryDriverService.save(form));
    }
}

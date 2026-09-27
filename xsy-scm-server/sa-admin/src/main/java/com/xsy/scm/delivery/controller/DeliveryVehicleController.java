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
import com.xsy.scm.delivery.service.DeliveryVehicleService;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryVehicleForm;
import com.xsy.scm.delivery.domain.entity.DeliveryVehicleEntity;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/delivery/vehicles")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM配送车辆")
public class DeliveryVehicleController {
    private final DeliveryVehicleService deliveryVehicleService;

    @GetMapping
    @SaCheckPermission(DeliveryPermission.VEHICLE_QUERY)
    public ResponseDTO<
            PageResult<
                    DeliveryVehicleEntity>> query(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(deliveryVehicleService.query(form));
    }

    @PostMapping
    @SaCheckPermission(DeliveryPermission.VEHICLE_EDIT)
    @OperateLog
    public ResponseDTO<
            Long> save(@Valid @RequestBody DeliveryVehicleForm form) {
        return ResponseDTO.ok(deliveryVehicleService.save(form));
    }
}

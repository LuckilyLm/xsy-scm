package net.lab1024.sa.admin.module.scm.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryVehicleService;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryVehicleEntity;
import net.lab1024.sa.base.common.domain.*;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/delivery/vehicles")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM配送车辆")
public class DeliveryVehicleController {
    private final DeliveryVehicleService service;

    @GetMapping
    @SaCheckPermission("scm:delivery:vehicle:query")
    public ResponseDTO<PageResult<DeliveryVehicleEntity>> query(@Valid @ModelAttribute DeliveryQueryForm form) {
        return ResponseDTO.ok(service.query(form));
    }

    @PostMapping
    @SaCheckPermission("scm:delivery:vehicle:edit")
    @OperateLog
    public ResponseDTO<Long> save(@Valid @RequestBody DeliveryVehicleForm form) {
        return ResponseDTO.ok(service.save(form));
    }
}

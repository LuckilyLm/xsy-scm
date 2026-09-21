package net.lab1024.sa.admin.module.scm.delivery.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryDriverService;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryDriverEntity;
import net.lab1024.sa.base.common.domain.*;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
@RestController @RequestMapping("/scm/delivery/drivers") @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name="SCM配送司机")
public class DeliveryDriverController {
    private final DeliveryDriverService service;
    @GetMapping @SaCheckPermission("scm:delivery:driver:query")
    public ResponseDTO<PageResult<DeliveryDriverEntity>> query(@Valid @ModelAttribute DeliveryQueryForm form) {return ResponseDTO.ok(service.query(form));}
    @PostMapping @SaCheckPermission("scm:delivery:driver:edit") @OperateLog
    public ResponseDTO<Long> save(@Valid @RequestBody DeliveryDriverForm form) {return ResponseDTO.ok(service.save(form));}
}

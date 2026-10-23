package com.xsy.scm.mall.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.mall.order.domain.form.MallCustomerWarehouseBindingForm;
import com.xsy.scm.mall.order.domain.form.MallDistrictWarehouseForm;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.mall.order.service.MallWarehouseAdminService;
import com.xsy.scm.warehouse.permission.WarehousePermission;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/warehouse/admin")
@RequiredArgsConstructor
public class MallWarehouseAdminController {
    private final MallWarehouseAdminService adminService;

    @GetMapping("/options")
    @SaCheckPermission(WarehousePermission.QUERY)
    public ResponseDTO<List<MallWarehouseOptionVO>> warehouses() {
        return ResponseDTO.ok(adminService.warehouses());
    }

    @GetMapping("/district-rules")
    @SaCheckPermission(WarehousePermission.QUERY)
    public ResponseDTO<List<MallWarehouseOptionVO>> districtRules(@RequestParam Integer districtCode) {
        return ResponseDTO.ok(adminService.districtRules(districtCode));
    }

    @PutMapping("/district-rules")
    @SaCheckPermission(WarehousePermission.UPDATE)
    public ResponseDTO<Void> saveDistrictRule(@Valid @RequestBody MallDistrictWarehouseForm form) {
        adminService.saveDistrictRule(form);
        return ResponseDTO.ok();
    }

    @DeleteMapping("/district-rules/{districtCode}/{warehouseId}")
    @SaCheckPermission(WarehousePermission.UPDATE)
    public ResponseDTO<Void> removeDistrictRule(@PathVariable Integer districtCode, @PathVariable Long warehouseId) {
        adminService.removeDistrictRule(districtCode, warehouseId);
        return ResponseDTO.ok();
    }

    @PutMapping("/customer-binding")
    @SaCheckPermission(value = {CustomerPermission.UPDATE, WarehousePermission.UPDATE}, mode = SaMode.AND)
    public ResponseDTO<Void> bindCustomer(@Valid @RequestBody MallCustomerWarehouseBindingForm form) {
        adminService.bindCustomer(form);
        return ResponseDTO.ok();
    }
}

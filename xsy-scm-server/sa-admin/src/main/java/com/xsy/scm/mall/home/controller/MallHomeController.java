package com.xsy.scm.mall.home.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.home.domain.vo.MallHomePageVO;
import com.xsy.scm.mall.home.service.MallHomeProjectionService;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/mall/home")
public class MallHomeController {
    private final MallHomeProjectionService projectionService;
    private final MallWarehouseService warehouseService;

    @GetMapping
    public ResponseDTO<MallHomePageVO> published(@RequestParam(required = false) @Positive Long warehouseId) {
        MallRequestSupport.requirePasswordChanged();
        var customer = MallRequestSupport.current();
        Long selectedWarehouse = warehouseId == null
                ? warehouseService.preferred(customer.getCustomerId(), null)
                : warehouseService.require(customer.getCustomerId(), null, warehouseId);
        return ResponseDTO.ok(projectionService.published(customer, selectedWarehouse));
    }
}

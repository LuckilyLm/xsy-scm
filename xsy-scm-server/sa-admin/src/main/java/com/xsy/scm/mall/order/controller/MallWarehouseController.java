package com.xsy.scm.mall.order.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.mall.order.service.MallWarehouseService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/warehouses")
@RequiredArgsConstructor
public class MallWarehouseController {
    private final MallWarehouseService warehouseService;

    @GetMapping
    public ResponseDTO<List<MallWarehouseOptionVO>> options(@RequestParam(required = false) Long addressId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(warehouseService.options(MallRequestSupport.current().getCustomerId(), addressId));
    }
}

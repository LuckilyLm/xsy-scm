package com.xsy.scm.mall.home.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.home.domain.vo.MallHomePageVO;
import com.xsy.scm.mall.home.domain.vo.MallThemeVO;
import com.xsy.scm.mall.home.service.MallHomeProjectionService;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.annoation.NoNeedLogin;
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

    /**
     * 匿名读取当前发布主题：登录页、改密页在取得客户身份前就需要全局配色。
     * 只开放主题指针本身，不扩大整个商城路径的匿名权限；草稿与其他数据一律不返回。
     */
    @GetMapping("/theme")
    @NoNeedLogin
    public ResponseDTO<MallThemeVO> theme() {
        return ResponseDTO.ok(projectionService.publishedTheme());
    }
}

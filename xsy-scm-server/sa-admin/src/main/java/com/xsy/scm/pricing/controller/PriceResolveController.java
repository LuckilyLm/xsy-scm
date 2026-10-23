package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.permission.ScmCrossDomainPermission;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.pricing.domain.form.PriceResolveForm;
import com.xsy.scm.pricing.domain.vo.PriceResolveResultVO;
import com.xsy.scm.pricing.domain.vo.PriceFormulaTrialVO;

import java.util.List;

import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.PriceResolver;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/resolve")
public class PriceResolveController {
    private final PriceResolver priceResolver;
    private final CustomerService customerService;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 后台读入口校验当前客户范围；共享定价服务仍供订单等内部业务路径调用。
     */
    @PostMapping
    @SaCheckPermission(PricingPermission.RESOLVE_QUERY)
    public ResponseDTO<PriceResolveResultVO> resolve(@Valid @RequestBody PriceResolveForm form) {
        customerService.requireInCurrentScope(form.getCustomerId());
        if (form.getWarehouseId() != null) {
            warehouseScopeGuard.require(form.getWarehouseId());
        }
        return ResponseDTO
            .ok(priceResolver.preview(form.getCustomerId(), form.getSkuIds(), form.getAt(), form.getWarehouseId()));
    }

    @PostMapping("/trial")
    @SaCheckPermission(value = {PricingPermission.RESOLVE_QUERY,
        ScmCrossDomainPermission.REPORT_COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<PriceFormulaTrialVO>> trial(@Valid @RequestBody PriceResolveForm form) {
        customerService.requireInCurrentScope(form.getCustomerId());
        if (form.getWarehouseId() == null) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        warehouseScopeGuard.require(form.getWarehouseId());
        return ResponseDTO.ok(
            priceResolver.resolve(form.getCustomerId(), form.getSkuIds(), form.getAt(), form.getWarehouseId()).stream()
                .map(price -> new PriceFormulaTrialVO(price.getSkuId(), price.getUnitPrice(),
                    price.getPriceSource() == null ? null : price.getPriceSource().name(),
                    price.getUnpricedReason() == null ? null : price.getUnpricedReason().name(),
                    price.getPriceBasisSnapshot()))
                .toList());
    }
}

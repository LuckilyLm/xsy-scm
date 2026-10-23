package com.xsy.scm.pricing.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.pricing.domain.form.TimePriceForm;
import com.xsy.scm.pricing.domain.form.TimePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.TimePriceVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.TimePriceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 时价维护。
 */
@RestController
@RequestMapping("/scm/pricing/time-price")
@RequiredArgsConstructor
public class TimePriceController {

    private final TimePriceService timePriceService;

    @PostMapping("/query")
    @SaCheckPermission(PricingPermission.TIME_PRICE_MANAGE)
    public ResponseDTO<PageResult<TimePriceVO>> query(@RequestBody @Valid TimePriceQueryForm form) {
        return ResponseDTO.ok(timePriceService.query(form));
    }

    @PostMapping("/add")
    @SaCheckPermission(PricingPermission.TIME_PRICE_MANAGE)
    public ResponseDTO<TimePriceVO> add(@RequestBody @Valid TimePriceForm form) {
        return ResponseDTO.ok(timePriceService.create(form, ScmOperator.current()));
    }

    @PostMapping("/update")
    @SaCheckPermission(PricingPermission.TIME_PRICE_MANAGE)
    public ResponseDTO<TimePriceVO> update(@RequestBody @Valid TimePriceForm form) {
        return ResponseDTO.ok(timePriceService.update(form, ScmOperator.current()));
    }

    @PostMapping("/delete")
    @SaCheckPermission(PricingPermission.TIME_PRICE_MANAGE)
    public ResponseDTO<String> delete(@RequestBody @Valid DeleteForm form) {
        timePriceService.delete(form.getId(), form.getVersion(), ScmOperator.current());
        return ResponseDTO.ok();
    }

    @Data
    public static class DeleteForm {
        @NotNull(message = "时价记录不能为空")
        private Long id;
        @NotNull(message = "版本不能为空")
        private Integer version;
    }
}

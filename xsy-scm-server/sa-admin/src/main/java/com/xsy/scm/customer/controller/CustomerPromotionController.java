package com.xsy.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerPromotionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 客户专属推广二维码（后台侧）。 */
@RestController
@RequestMapping("/scm/customer/promotion")
@RequiredArgsConstructor
public class CustomerPromotionController {

    private final CustomerPromotionService customerPromotionService;

    /** 生成推广码：该客户上的旧码当场失效。 */
    @PostMapping("/create")
    @SaCheckPermission(CustomerPermission.PROMOTION_MANAGE)
    public ResponseDTO<CustomerPromotionVO> create(@RequestBody @Valid CustomerForm form) {
        return ResponseDTO.ok(customerPromotionService.create(form.getCustomerId(), ScmOperator.current()));
    }

    @PostMapping("/revoke")
    @SaCheckPermission(CustomerPermission.PROMOTION_MANAGE)
    public ResponseDTO<String> revoke(@RequestBody @Valid IdForm form) {
        customerPromotionService.revoke(form.getId());
        return ResponseDTO.ok();
    }

    @GetMapping("/list")
    @SaCheckPermission(CustomerPermission.PROMOTION_MANAGE)
    public ResponseDTO<List<CustomerPromotionVO>> list(@RequestParam @NotNull Long customerId) {
        return ResponseDTO.ok(customerPromotionService.list(customerId));
    }

    @GetMapping("/scans")
    @SaCheckPermission(CustomerPermission.PROMOTION_MANAGE)
    public ResponseDTO<List<CustomerPromotionVO.Scan>> scans(@RequestParam @NotNull Long codeId,
            @RequestParam(required = false) Integer limit) {
        return ResponseDTO.ok(customerPromotionService.scans(codeId, limit));
    }

    /** 最近一次扫码归属；新增客户时用来预填业务员。 */
    @GetMapping("/attribution")
    @SaCheckPermission(CustomerPermission.PROMOTION_MANAGE)
    public ResponseDTO<CustomerPromotionVO.Scan> attribution() {
        return ResponseDTO.ok(customerPromotionService.latestAttribution());
    }

    @Data
    public static class CustomerForm {
        @NotNull(message = "客户不能为空")
        private Long customerId;
    }

    @Data
    public static class IdForm {
        @NotNull(message = "推广码记录不能为空")
        private Long id;
    }
}

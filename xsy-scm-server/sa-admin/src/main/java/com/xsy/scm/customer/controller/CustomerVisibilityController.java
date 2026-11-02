package com.xsy.scm.customer.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.customer.domain.form.CustomerVisibilityQueryForm;
import com.xsy.scm.customer.domain.form.CustomerVisibilityReplaceForm;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilitySummaryVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerSkuVisibilityService;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CustomerVisibilityController {
    private final CustomerSkuVisibilityService customerSkuVisibilityService;
    private final CustomerService customerService;

    /** 客户详情页调整可售商品：策略与授权规格整体替换。 */
    @PostMapping("/scm/customer/visibility/replace")
    @SaCheckPermission(CustomerPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> replace(@Valid @RequestBody CustomerVisibilityReplaceForm form) {
        customerService.replaceVisibility(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/scm/customer/visibility/reverse/query")
    @SaCheckPermission(CustomerPermission.VISIBILITY_QUERY)
    public ResponseDTO<PageResult<CustomerSkuVisibilityReverseVO>> reverse(
        @Valid @RequestBody CustomerVisibilityQueryForm queryForm) {
        return ResponseDTO.ok(customerSkuVisibilityService.reverse(queryForm));
    }

    /** 按客户汇总可见商品与规格数，列表页的客户卡片使用。 */
    @GetMapping("/scm/customer/visibility/customer-summary")
    @SaCheckPermission(CustomerPermission.VISIBILITY_QUERY)
    public ResponseDTO<List<CustomerSkuVisibilitySummaryVO>> customerSummary() {
        return ResponseDTO.ok(customerSkuVisibilityService.summarizeCustomers());
    }
}

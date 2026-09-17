package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerDiscountVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerDiscountService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户折扣率（计算折前价） Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerDiscountController {

    @Resource
    private CustomerDiscountService customerDiscountService;

    @Operation(summary = "分页查询客户折扣率 @author xsy-scm")
    @PostMapping("/customer/discount/query")
    @SaCheckPermission("customerDiscount:query")
    public ResponseDTO<PageResult<CustomerDiscountVO>> query(@RequestBody @Valid CustomerDiscountQueryForm queryForm) {
        return customerDiscountService.query(queryForm);
    }

    @Operation(summary = "添加客户折扣率 @author xsy-scm")
    @PostMapping("/customer/discount/add")
    @SaCheckPermission("customerDiscount:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerDiscountAddForm addForm) {
        return customerDiscountService.add(addForm);
    }

    @Operation(summary = "更新客户折扣率 @author xsy-scm")
    @PostMapping("/customer/discount/update")
    @SaCheckPermission("customerDiscount:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerDiscountUpdateForm updateForm) {
        return customerDiscountService.update(updateForm);
    }

    @Operation(summary = "删除客户折扣率 @author xsy-scm")
    @GetMapping("/customer/discount/delete/{discountId}")
    @SaCheckPermission("customerDiscount:delete")
    public ResponseDTO<String> delete(@PathVariable Long discountId) {
        return customerDiscountService.delete(discountId);
    }

    @Operation(summary = "批量删除客户折扣率 @author xsy-scm")
    @PostMapping("/customer/discount/batchDelete")
    @SaCheckPermission("customerDiscount:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerDiscountService.batchDelete(idList);
    }
}

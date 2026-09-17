package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerPeriodVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerPeriodService;
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
 * 客户账期 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerPeriodController {

    @Resource
    private CustomerPeriodService customerPeriodService;

    @Operation(summary = "分页查询客户账期 @author xsy-scm")
    @PostMapping("/customer/period/query")
    @SaCheckPermission("customer:period:query")
    public ResponseDTO<PageResult<CustomerPeriodVO>> query(@RequestBody @Valid CustomerPeriodQueryForm queryForm) {
        return customerPeriodService.query(queryForm);
    }

    @Operation(summary = "添加客户账期 @author xsy-scm")
    @PostMapping("/customer/period/add")
    @SaCheckPermission("customer:period:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerPeriodAddForm addForm) {
        return customerPeriodService.add(addForm);
    }

    @Operation(summary = "更新客户账期 @author xsy-scm")
    @PostMapping("/customer/period/update")
    @SaCheckPermission("customer:period:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerPeriodUpdateForm updateForm) {
        return customerPeriodService.update(updateForm);
    }

    @Operation(summary = "删除客户账期 @author xsy-scm")
    @GetMapping("/customer/period/delete/{periodId}")
    @SaCheckPermission("customer:period:delete")
    public ResponseDTO<String> delete(@PathVariable Long periodId) {
        return customerPeriodService.delete(periodId);
    }

    @Operation(summary = "批量删除客户账期 @author xsy-scm")
    @PostMapping("/customer/period/batchDelete")
    @SaCheckPermission("customer:period:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerPeriodService.batchDelete(idList);
    }
}

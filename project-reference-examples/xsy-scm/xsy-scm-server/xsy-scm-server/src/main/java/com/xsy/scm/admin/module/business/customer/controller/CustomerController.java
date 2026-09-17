package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerService;
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

import java.util.List;

/**
 * 客户 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/customer/query、/customer/add、/customer/update、/customer/delete/{id}</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerController {

    @Resource
    private CustomerService customerService;

    @Operation(summary = "分页查询客户 @author xsy-scm")
    @PostMapping("/customer/query")
    @SaCheckPermission("customer:query")
    public ResponseDTO<PageResult<CustomerVO>> query(@RequestBody @Valid CustomerQueryForm queryForm) {
        return customerService.query(queryForm);
    }

    @Operation(summary = "添加客户 @author xsy-scm")
    @PostMapping("/customer/add")
    @SaCheckPermission("customer:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerAddForm addForm) {
        return customerService.add(addForm);
    }

    @Operation(summary = "更新客户 @author xsy-scm")
    @PostMapping("/customer/update")
    @SaCheckPermission("customer:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerUpdateForm updateForm) {
        return customerService.update(updateForm);
    }

    @Operation(summary = "删除客户 @author xsy-scm")
    @GetMapping("/customer/delete/{customerId}")
    @SaCheckPermission("customer:delete")
    public ResponseDTO<String> delete(@PathVariable Long customerId) {
        return customerService.delete(customerId);
    }

    @Operation(summary = "批量删除客户 @author xsy-scm")
    @PostMapping("/customer/batchDelete")
    @SaCheckPermission("customer:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerService.batchDelete(idList);
    }

    @Operation(summary = "查询所有客户 @author xsy-scm")
    @GetMapping("/customer/queryAll")
    @SaCheckPermission("customer:query")
    public ResponseDTO<List<CustomerVO>> queryAll() {
        return customerService.queryAll();
    }
}

package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerProductAliasVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerProductAliasService;
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
 * 客户商品别名 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerProductAliasController {

    @Resource
    private CustomerProductAliasService customerProductAliasService;

    @Operation(summary = "分页查询客户商品别名 @author xsy-scm")
    @PostMapping("/customer/productAlias/query")
    @SaCheckPermission("customerProductAlias:query")
    public ResponseDTO<PageResult<CustomerProductAliasVO>> query(@RequestBody @Valid CustomerProductAliasQueryForm queryForm) {
        return customerProductAliasService.query(queryForm);
    }

    @Operation(summary = "添加客户商品别名 @author xsy-scm")
    @PostMapping("/customer/productAlias/add")
    @SaCheckPermission("customerProductAlias:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerProductAliasAddForm addForm) {
        return customerProductAliasService.add(addForm);
    }

    @Operation(summary = "更新客户商品别名 @author xsy-scm")
    @PostMapping("/customer/productAlias/update")
    @SaCheckPermission("customerProductAlias:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerProductAliasUpdateForm updateForm) {
        return customerProductAliasService.update(updateForm);
    }

    @Operation(summary = "删除客户商品别名 @author xsy-scm")
    @GetMapping("/customer/productAlias/delete/{aliasId}")
    @SaCheckPermission("customerProductAlias:delete")
    public ResponseDTO<String> delete(@PathVariable Long aliasId) {
        return customerProductAliasService.delete(aliasId);
    }

    @Operation(summary = "批量删除客户商品别名 @author xsy-scm")
    @PostMapping("/customer/productAlias/batchDelete")
    @SaCheckPermission("customerProductAlias:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerProductAliasService.batchDelete(idList);
    }
}

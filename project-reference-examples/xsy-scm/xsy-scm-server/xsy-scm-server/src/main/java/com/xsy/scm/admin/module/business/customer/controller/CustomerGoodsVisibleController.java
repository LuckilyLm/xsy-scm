package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerGoodsVisibleVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerGoodsVisibleService;
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
 * 客户商品可见性 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerGoodsVisibleController {

    @Resource
    private CustomerGoodsVisibleService customerGoodsVisibleService;

    @Operation(summary = "分页查询客户商品可见性 @author xsy-scm")
    @PostMapping("/customer/goodsVisible/query")
    @SaCheckPermission("customer:goodsVisible:query")
    public ResponseDTO<PageResult<CustomerGoodsVisibleVO>> query(@RequestBody @Valid CustomerGoodsVisibleQueryForm queryForm) {
        return customerGoodsVisibleService.query(queryForm);
    }

    @Operation(summary = "添加客户商品可见性 @author xsy-scm")
    @PostMapping("/customer/goodsVisible/add")
    @SaCheckPermission("customer:goodsVisible:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerGoodsVisibleAddForm addForm) {
        return customerGoodsVisibleService.add(addForm);
    }

    @Operation(summary = "更新客户商品可见性 @author xsy-scm")
    @PostMapping("/customer/goodsVisible/update")
    @SaCheckPermission("customer:goodsVisible:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerGoodsVisibleUpdateForm updateForm) {
        return customerGoodsVisibleService.update(updateForm);
    }

    @Operation(summary = "删除客户商品可见性 @author xsy-scm")
    @GetMapping("/customer/goodsVisible/delete/{id}")
    @SaCheckPermission("customer:goodsVisible:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return customerGoodsVisibleService.delete(id);
    }

    @Operation(summary = "批量删除客户商品可见性 @author xsy-scm")
    @PostMapping("/customer/goodsVisible/batchDelete")
    @SaCheckPermission("customer:goodsVisible:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerGoodsVisibleService.batchDelete(idList);
    }
}

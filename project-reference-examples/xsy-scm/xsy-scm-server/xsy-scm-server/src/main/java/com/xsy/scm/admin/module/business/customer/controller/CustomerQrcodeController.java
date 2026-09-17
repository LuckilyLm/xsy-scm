package com.xsy.scm.admin.module.business.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerQrcodeVO;
import com.xsy.scm.admin.module.business.customer.service.CustomerQrcodeService;
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
 * 业务员推广二维码 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_CUSTOMER)
public class CustomerQrcodeController {

    @Resource
    private CustomerQrcodeService customerQrcodeService;

    @Operation(summary = "分页查询推广二维码 @author xsy-scm")
    @PostMapping("/customer/qrcode/query")
    @SaCheckPermission("customer:qrcode:query")
    public ResponseDTO<PageResult<CustomerQrcodeVO>> query(@RequestBody @Valid CustomerQrcodeQueryForm queryForm) {
        return customerQrcodeService.query(queryForm);
    }

    @Operation(summary = "添加推广二维码 @author xsy-scm")
    @PostMapping("/customer/qrcode/add")
    @SaCheckPermission("customer:qrcode:add")
    public ResponseDTO<String> add(@RequestBody @Valid CustomerQrcodeAddForm addForm) {
        return customerQrcodeService.add(addForm);
    }

    @Operation(summary = "更新推广二维码 @author xsy-scm")
    @PostMapping("/customer/qrcode/update")
    @SaCheckPermission("customer:qrcode:update")
    public ResponseDTO<String> update(@RequestBody @Valid CustomerQrcodeUpdateForm updateForm) {
        return customerQrcodeService.update(updateForm);
    }

    @Operation(summary = "删除推广二维码 @author xsy-scm")
    @GetMapping("/customer/qrcode/delete/{qrcodeId}")
    @SaCheckPermission("customer:qrcode:delete")
    public ResponseDTO<String> delete(@PathVariable Long qrcodeId) {
        return customerQrcodeService.delete(qrcodeId);
    }

    @Operation(summary = "批量删除推广二维码 @author xsy-scm")
    @PostMapping("/customer/qrcode/batchDelete")
    @SaCheckPermission("customer:qrcode:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return customerQrcodeService.batchDelete(idList);
    }
}

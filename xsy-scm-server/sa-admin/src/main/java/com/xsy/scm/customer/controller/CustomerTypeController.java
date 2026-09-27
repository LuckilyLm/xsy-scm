package com.xsy.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.customer.domain.form.CustomerTypeAddForm;
import com.xsy.scm.customer.domain.form.CustomerTypeDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerTypeQueryForm;
import com.xsy.scm.customer.domain.form.CustomerTypeUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerTypeVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerTypeService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 客户类型（可维护字典）。
 *
 * <p>没有独立的状态端点：状态随新增 / 编辑表单带入（legacy 不变量 T8）。
 */
@RestController
@RequestMapping("/scm/customer/type")
@Tag(name = "SCM 客户类型")
@RequiredArgsConstructor
public class CustomerTypeController {

    private final CustomerTypeService customerTypeService;

    @PostMapping("/query")
    @SaCheckPermission(CustomerPermission.TYPE_QUERY)
    public ResponseDTO<PageResult<CustomerTypeVO>> query(@Valid @RequestBody CustomerTypeQueryForm form) {
        return ResponseDTO.ok(customerTypeService.query(form));
    }

    @PostMapping("/add")
    @SaCheckPermission(CustomerPermission.TYPE_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerTypeAddForm form) {
        return ResponseDTO.ok(customerTypeService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(CustomerPermission.TYPE_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerTypeUpdateForm form) {
        customerTypeService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(CustomerPermission.TYPE_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerTypeDeleteForm form) {
        customerTypeService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission(CustomerPermission.TYPE_QUERY)
    public ResponseDTO<List<CustomerTypeVO>> optionList() {
        return ResponseDTO.ok(customerTypeService.optionList());
    }
}

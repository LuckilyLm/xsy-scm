package com.xsy.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.customer.domain.form.CustomerGroupAddForm;
import com.xsy.scm.customer.domain.form.CustomerGroupDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerGroupQueryForm;
import com.xsy.scm.customer.domain.form.CustomerGroupUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerGroupVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户分组维护。分组只承载价格约定与成员关系，不改变客户状态或客户类型。
 */
@RestController
@RequestMapping("/scm/customer/group")
@Tag(name = "SCM 客户分组")
@RequiredArgsConstructor
public class CustomerGroupController {

    private final CustomerGroupService customerGroupService;

    @PostMapping("/query")
    @SaCheckPermission(CustomerPermission.GROUP_QUERY)
    public ResponseDTO<PageResult<CustomerGroupVO>> query(@Valid @RequestBody CustomerGroupQueryForm form) {
        return ResponseDTO.ok(customerGroupService.query(form));
    }

    @GetMapping("/options")
    @SaCheckPermission(CustomerPermission.GROUP_QUERY)
    public ResponseDTO<List<CustomerGroupVO>> options() {
        return ResponseDTO.ok(customerGroupService.optionList());
    }

    @GetMapping("/{groupId}")
    @SaCheckPermission(CustomerPermission.GROUP_QUERY)
    public ResponseDTO<CustomerGroupVO> detail(@PathVariable Long groupId) {
        return ResponseDTO.ok(customerGroupService.detail(groupId));
    }

    @PostMapping("/add")
    @SaCheckPermission(CustomerPermission.GROUP_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerGroupAddForm form) {
        return ResponseDTO.ok(customerGroupService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(CustomerPermission.GROUP_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerGroupUpdateForm form) {
        customerGroupService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(CustomerPermission.GROUP_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerGroupDeleteForm form) {
        customerGroupService.delete(form);
        return ResponseDTO.ok();
    }
}

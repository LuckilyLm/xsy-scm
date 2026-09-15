package net.lab1024.sa.admin.module.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerTypeVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerTypeService;
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

    private final CustomerTypeService service;

    @PostMapping("/query")
    @SaCheckPermission("scm:customer:type:query")
    public ResponseDTO<PageResult<CustomerTypeVO>> query(@Valid @RequestBody CustomerTypeQueryForm form) {
        return ResponseDTO.ok(service.query(form));
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:customer:type:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerTypeAddForm form) {
        return ResponseDTO.ok(service.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:customer:type:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerTypeUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:customer:type:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerTypeDeleteForm form) {
        service.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission("scm:customer:type:query")
    public ResponseDTO<List<CustomerTypeVO>> optionList() {
        return ResponseDTO.ok(service.optionList());
    }
}

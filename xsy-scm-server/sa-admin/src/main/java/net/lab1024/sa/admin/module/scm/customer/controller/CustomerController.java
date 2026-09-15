package net.lab1024.sa.admin.module.scm.customer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerDetailVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerOptionVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerQueryService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
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
 * SCM 客户档案。
 *
 * <p>写端点全部带 {@code @OperateLog}（修正 legacy 客户域零操作日志的缺陷 D3），
 * 全部带 {@code @SaCheckPermission}（修正 legacy 零权限注解的缺陷 D2）。
 */
@RestController
@RequestMapping("/scm/customer")
@Tag(name = "SCM 客户档案")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    private final CustomerQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:customer:query")
    public ResponseDTO<PageResult<CustomerVO>> query(@Valid @RequestBody CustomerQueryForm form) {
        return ResponseDTO.ok(queryService.query(form));
    }

    @GetMapping("/detail/{customerId}")
    @SaCheckPermission("scm:customer:query")
    public ResponseDTO<CustomerDetailVO> detail(@PathVariable Long customerId) {
        return ResponseDTO.ok(queryService.detail(customerId));
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:customer:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerAddForm form) {
        return ResponseDTO.ok(service.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:customer:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/updateStatus")
    @SaCheckPermission("scm:customer:status")
    @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody CustomerStatusForm form) {
        service.updateStatus(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:customer:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerDeleteForm form) {
        service.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission("scm:customer:query")
    public ResponseDTO<List<CustomerOptionVO>> optionList() {
        return ResponseDTO.ok(queryService.optionList());
    }
}

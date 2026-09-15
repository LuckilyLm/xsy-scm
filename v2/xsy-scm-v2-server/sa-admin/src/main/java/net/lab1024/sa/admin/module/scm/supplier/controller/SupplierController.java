package net.lab1024.sa.admin.module.scm.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierDeleteForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierStatusForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierUpdateForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierDetailVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierOptionVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierQueryService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
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
 * SCM 供应商档案。
 *
 * <p>更新端点刻意不接收 {@code status}：{@link SupplierUpdateForm} 没有该字段，
 * 客户端即使传了也会被 Jackson 忽略（legacy 不变量 S6）。
 */
@RestController
@RequestMapping("/scm/supplier")
@Tag(name = "SCM 供应商档案")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    private final SupplierQueryService queryService;

    @PostMapping("/query")
    @SaCheckPermission("scm:supplier:query")
    public ResponseDTO<PageResult<SupplierVO>> query(@Valid @RequestBody SupplierQueryForm form) {
        return ResponseDTO.ok(queryService.query(form));
    }

    @GetMapping("/detail/{supplierId}")
    @SaCheckPermission("scm:supplier:query")
    public ResponseDTO<SupplierDetailVO> detail(@PathVariable Long supplierId) {
        return ResponseDTO.ok(queryService.detail(supplierId));
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:supplier:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody SupplierAddForm form) {
        return ResponseDTO.ok(service.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:supplier:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody SupplierUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/updateStatus")
    @SaCheckPermission("scm:supplier:status")
    @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody SupplierStatusForm form) {
        service.updateStatus(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:supplier:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody SupplierDeleteForm form) {
        service.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission("scm:supplier:query")
    public ResponseDTO<List<SupplierOptionVO>> optionList() {
        return ResponseDTO.ok(queryService.optionList());
    }
}

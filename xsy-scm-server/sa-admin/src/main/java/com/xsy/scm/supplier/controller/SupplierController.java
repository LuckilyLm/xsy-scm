package com.xsy.scm.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.supplier.domain.form.SupplierAddForm;
import com.xsy.scm.supplier.domain.form.SupplierDeleteForm;
import com.xsy.scm.supplier.domain.form.SupplierQueryForm;
import com.xsy.scm.supplier.domain.form.SupplierStatusForm;
import com.xsy.scm.supplier.domain.form.SupplierUpdateForm;
import com.xsy.scm.supplier.domain.vo.SupplierDetailVO;
import com.xsy.scm.supplier.domain.vo.SupplierOptionVO;
import com.xsy.scm.supplier.domain.vo.SupplierVO;
import com.xsy.scm.supplier.permission.SupplierPermission;
import com.xsy.scm.supplier.service.SupplierQueryService;
import com.xsy.scm.supplier.service.SupplierService;
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
 * <p>
 * 更新端点不接收 {@code status}：{@link SupplierUpdateForm} 没有该字段，客户端传入的未知属性由 Jackson 忽略。
 */
@RestController
@RequestMapping("/scm/supplier")
@Tag(name = "SCM 供应商档案")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    private final SupplierQueryService supplierQueryService;

    @PostMapping("/query")
    @SaCheckPermission(SupplierPermission.QUERY)
    public ResponseDTO<PageResult<SupplierVO>> query(@Valid @RequestBody SupplierQueryForm form) {
        return ResponseDTO.ok(supplierQueryService.query(form));
    }

    @GetMapping("/detail/{supplierId}")
    @SaCheckPermission(SupplierPermission.QUERY)
    public ResponseDTO<SupplierDetailVO> detail(@PathVariable Long supplierId) {
        return ResponseDTO.ok(supplierQueryService.detail(supplierId));
    }

    @PostMapping("/add")
    @SaCheckPermission(SupplierPermission.ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody SupplierAddForm form) {
        return ResponseDTO.ok(supplierService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(SupplierPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody SupplierUpdateForm form) {
        supplierService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/updateStatus")
    @SaCheckPermission(SupplierPermission.STATUS)
    @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody SupplierStatusForm form) {
        supplierService.updateStatus(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(SupplierPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody SupplierDeleteForm form) {
        supplierService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/option/list")
    @SaCheckPermission(SupplierPermission.QUERY)
    public ResponseDTO<List<SupplierOptionVO>> optionList() {
        return ResponseDTO.ok(supplierQueryService.optionList());
    }
}

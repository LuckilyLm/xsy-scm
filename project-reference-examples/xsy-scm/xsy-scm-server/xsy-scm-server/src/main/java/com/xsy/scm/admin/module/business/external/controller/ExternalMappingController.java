package com.xsy.scm.admin.module.business.external.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingAddForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingImportForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingQueryForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingUpdateForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalMappingVO;
import com.xsy.scm.admin.module.business.external.service.ExternalMappingService;
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
 * 外部平台映射 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "外部平台对接-映射")
public class ExternalMappingController {

    @Resource
    private ExternalMappingService externalMappingService;

    @Operation(summary = "分页查询外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/query")
    @SaCheckPermission("externalMapping:query")
    public ResponseDTO<PageResult<ExternalMappingVO>> query(@RequestBody @Valid ExternalMappingQueryForm queryForm) {
        return externalMappingService.query(queryForm);
    }

    @Operation(summary = "导出外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/export")
    @SaCheckPermission("externalMapping:export")
    public ResponseDTO<List<ExternalMappingVO>> export(@RequestBody @Valid ExternalMappingQueryForm queryForm) {
        return externalMappingService.export(queryForm);
    }

    @Operation(summary = "添加外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/add")
    @SaCheckPermission("externalMapping:add")
    public ResponseDTO<String> add(@RequestBody @Valid ExternalMappingAddForm addForm) {
        return externalMappingService.add(addForm);
    }

    @Operation(summary = "批量导入外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/import")
    @SaCheckPermission("externalMapping:import")
    public ResponseDTO<String> importMappings(@RequestBody @Valid ExternalMappingImportForm importForm) {
        return externalMappingService.importMappings(importForm);
    }

    @Operation(summary = "更新外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/update")
    @SaCheckPermission("externalMapping:update")
    public ResponseDTO<String> update(@RequestBody @Valid ExternalMappingUpdateForm updateForm) {
        return externalMappingService.update(updateForm);
    }

    @Operation(summary = "删除外部平台映射 @author xsy-scm")
    @GetMapping("/external/mapping/delete/{mappingId}")
    @SaCheckPermission("externalMapping:delete")
    public ResponseDTO<String> delete(@PathVariable Long mappingId) {
        return externalMappingService.delete(mappingId);
    }

    @Operation(summary = "批量删除外部平台映射 @author xsy-scm")
    @PostMapping("/external/mapping/batchDelete")
    @SaCheckPermission("externalMapping:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return externalMappingService.batchDelete(idList);
    }
}

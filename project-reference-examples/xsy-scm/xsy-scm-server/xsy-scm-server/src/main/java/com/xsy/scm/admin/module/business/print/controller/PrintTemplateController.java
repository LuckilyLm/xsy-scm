package com.xsy.scm.admin.module.business.print.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.print.domain.form.PrintPreviewForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateAddForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateQueryForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateUpdateForm;
import com.xsy.scm.admin.module.business.print.domain.vo.PrintPreviewVO;
import com.xsy.scm.admin.module.business.print.domain.vo.PrintTemplateVO;
import com.xsy.scm.admin.module.business.print.service.PrintTemplateService;
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
 * 打印模板 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRINT)
public class PrintTemplateController {

    @Resource
    private PrintTemplateService printTemplateService;

    @Operation(summary = "分页查询打印模板 @author xsy-scm")
    @PostMapping("/printTemplate/query")
    @SaCheckPermission("printTemplate:query")
    public ResponseDTO<PageResult<PrintTemplateVO>> query(@RequestBody @Valid PrintTemplateQueryForm queryForm) {
        return printTemplateService.query(queryForm);
    }

    @Operation(summary = "添加打印模板 @author xsy-scm")
    @PostMapping("/printTemplate/add")
    @SaCheckPermission("printTemplate:add")
    public ResponseDTO<String> add(@RequestBody @Valid PrintTemplateAddForm addForm) {
        return printTemplateService.add(addForm);
    }

    @Operation(summary = "更新打印模板 @author xsy-scm")
    @PostMapping("/printTemplate/update")
    @SaCheckPermission("printTemplate:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrintTemplateUpdateForm updateForm) {
        return printTemplateService.update(updateForm);
    }

    @Operation(summary = "设为默认打印模板 @author xsy-scm")
    @PostMapping("/printTemplate/setDefault/{templateId}")
    @SaCheckPermission("printTemplate:setDefault")
    public ResponseDTO<String> setDefault(@PathVariable Long templateId) {
        return printTemplateService.setDefault(templateId);
    }

    @Operation(summary = "打印预览（服务端渲染） @author xsy-scm")
    @PostMapping("/printTemplate/preview")
    @SaCheckPermission("printTemplate:query")
    public ResponseDTO<PrintPreviewVO> preview(@RequestBody @Valid PrintPreviewForm previewForm) {
        return printTemplateService.preview(previewForm);
    }

    @Operation(summary = "删除打印模板 @author xsy-scm")
    @GetMapping("/printTemplate/delete/{templateId}")
    @SaCheckPermission("printTemplate:delete")
    public ResponseDTO<String> delete(@PathVariable Long templateId) {
        return printTemplateService.delete(templateId);
    }

    @Operation(summary = "批量删除打印模板 @author xsy-scm")
    @PostMapping("/printTemplate/batchDelete")
    @SaCheckPermission("printTemplate:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return printTemplateService.batchDelete(idList);
    }
}

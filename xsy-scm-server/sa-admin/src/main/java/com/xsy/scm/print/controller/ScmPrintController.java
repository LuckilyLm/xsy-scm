package com.xsy.scm.print.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintBatchForm;
import com.xsy.scm.print.domain.form.ScmPrintCustomerTemplateForm;
import com.xsy.scm.print.domain.form.ScmPrintRecordQueryForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateQueryForm;
import com.xsy.scm.print.domain.vo.ScmPrintBatchResultVO;
import com.xsy.scm.print.domain.vo.ScmPrintCustomerTemplateVO;
import com.xsy.scm.print.domain.vo.ScmPrintDocumentTypeVO;
import com.xsy.scm.print.domain.vo.ScmPrintFieldCatalogVO;
import com.xsy.scm.print.domain.vo.ScmPrintRecordVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.domain.vo.ScmPrintTemplateVO;
import com.xsy.scm.print.permission.ScmPrintPermission;
import com.xsy.scm.print.service.ScmPrintBatchService;
import com.xsy.scm.print.service.ScmPrintCustomerBindingService;
import com.xsy.scm.print.service.ScmPrintService;
import com.xsy.scm.print.service.ScmPrintTemplateService;

import java.util.List;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 打印接口按单据及模板权限校验，不能通过打印接口绕过原业务数据范围。
 */
@RestController
@RequestMapping("/scm/print")
@Tag(name = "SCM 打印中心")
@RequiredArgsConstructor
public class ScmPrintController {

    private final ScmPrintTemplateService scmPrintTemplateService;

    private final ScmPrintCustomerBindingService scmPrintCustomerBindingService;

    private final ScmPrintService scmPrintService;

    private final ScmPrintBatchService scmPrintBatchService;

    // 模板

    @PostMapping("/template/query")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<PageResult<ScmPrintTemplateVO>> queryTemplate(
        @Valid @RequestBody ScmPrintTemplateQueryForm form) {
        return ResponseDTO.ok(scmPrintTemplateService.queryPage(form));
    }

    /** 字段目录：模板配置页据此渲染可选项。 */
    @GetMapping("/template/catalog")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<ScmPrintFieldCatalogVO> catalog(@RequestParam("documentType") String documentType) {
        return ResponseDTO.ok(scmPrintTemplateService.catalog(documentType));
    }

    /** 可配置打印的单据类型清单，供前端下拉使用。 */
    @GetMapping("/template/document-types")
    @SaCheckPermission(value = {ScmPrintPermission.TEMPLATE_QUERY, ScmPrintPermission.RECORD_QUERY},
        mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ResponseDTO<List<ScmPrintDocumentTypeVO>> documentTypes() {
        return ResponseDTO.ok(scmPrintTemplateService.documentTypes());
    }

    @GetMapping("/template/{id}")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<ScmPrintTemplateVO> templateDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(scmPrintTemplateService.detail(id));
    }

    @PostMapping("/template")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_ADD)
    @OperateLog
    public ResponseDTO<Long> createTemplate(@Valid @RequestBody ScmPrintTemplateForm form) {
        return ResponseDTO.ok(scmPrintTemplateService.create(form));
    }

    @PostMapping("/template/update")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_UPDATE)
    @OperateLog
    public ResponseDTO<String> updateTemplate(@Valid @RequestBody ScmPrintTemplateForm form) {
        scmPrintTemplateService.update(form);
        return ResponseDTO.ok();
    }

    /** 设为默认模板，同类型只保留一个默认。 */
    @PostMapping("/template/{id}/default")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_UPDATE)
    @OperateLog
    public ResponseDTO<String> setDefaultTemplate(@PathVariable("id") Long id) {
        scmPrintTemplateService.setDefault(id);
        return ResponseDTO.ok();
    }

    @PostMapping("/template/{id}/delete")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_DELETE)
    @OperateLog
    public ResponseDTO<String> deleteTemplate(@PathVariable("id") Long id, @RequestParam("version") Integer version) {
        scmPrintTemplateService.delete(id, version);
        return ResponseDTO.ok();
    }

    // 客户专属模板

    /** 绑定列表；按单据类型、客户筛选均可省略。 */
    @GetMapping("/template/customer-binding")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<List<ScmPrintCustomerTemplateVO>> queryCustomerBindings(
        @RequestParam(value = "documentType", required = false) String documentType,
        @RequestParam(value = "customerId", required = false) Long customerId) {
        return ResponseDTO.ok(scmPrintCustomerBindingService.query(documentType, customerId));
    }

    /** 绑定或换绑客户专属模板；同客户同类型只保留一条有效绑定。 */
    @PostMapping("/template/customer-binding")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_UPDATE)
    @OperateLog
    public ResponseDTO<String> bindCustomerTemplate(@Valid @RequestBody ScmPrintCustomerTemplateForm form) {
        scmPrintCustomerBindingService.bind(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/template/customer-binding/unbind")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_UPDATE)
    @OperateLog
    public ResponseDTO<String> unbindCustomerTemplate(@RequestParam("documentType") String documentType,
        @RequestParam("customerId") Long customerId) {
        scmPrintCustomerBindingService.unbind(documentType, customerId);
        return ResponseDTO.ok();
    }

    // 渲染与打印

    @GetMapping("/{documentType}/{businessId}/templates")
    public ResponseDTO<List<ScmPrintTemplateVO>> templateOptions(@PathVariable("documentType") String documentType,
        @PathVariable("businessId") Long businessId) {
        return ResponseDTO.ok(scmPrintService.templateOptions(documentType, businessId));
    }

    /**
     * 打印预览：只读，不计次、不留痕；{@code templateId} 为空时用默认模板。
     */
    @GetMapping("/{documentType}/{businessId}/preview")
    public ResponseDTO<ScmPrintRenderVO> preview(@PathVariable("documentType") String documentType,
        @PathVariable("businessId") Long businessId,
        @RequestParam(value = "templateId", required = false) Long templateId) {
        return ResponseDTO.ok(scmPrintService.preview(documentType, businessId, templateId));
    }

    /** 正式打印：保存模板版本、模板内容和版面内容，并返回这份结果。 */
    @PostMapping("/{documentType}/{businessId}/print")
    @OperateLog
    public ResponseDTO<ScmPrintRenderVO> print(@PathVariable("documentType") String documentType,
        @PathVariable("businessId") Long businessId, @Valid @RequestBody ScmPrintActionForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(scmPrintService.print(documentType, businessId, form, idempotencyKey));
    }

    /**
     * 20:00 批量预览客户分拣单/送货单：按配送日期取当天已确认订单，逐单返回版面，不写打印记录。
     */
    @PostMapping("/batch/preview")
    public ResponseDTO<ScmPrintBatchResultVO> batchPreview(@Valid @RequestBody ScmPrintBatchForm form) {
        return ResponseDTO.ok(scmPrintBatchService.preview(form));
    }

    /**
     * 20:00 批量正式打印：每单独立保存打印记录；一张失败不回滚其他单，结果里返回失败明细。
     */
    @PostMapping("/batch/print")
    @OperateLog
    public ResponseDTO<ScmPrintBatchResultVO> batchPrint(@Valid @RequestBody ScmPrintBatchForm form) {
        return ResponseDTO.ok(scmPrintBatchService.print(form));
    }

    /** 历史重印：只读当时保存的内容，并按当前调用者的金额权限重新过滤。 */
    @GetMapping("/record/{recordId}/reprint")
    @SaCheckPermission(ScmPrintPermission.RECORD_QUERY)
    public ResponseDTO<ScmPrintRenderVO> reprint(@PathVariable("recordId") Long recordId) {
        return ResponseDTO.ok(scmPrintService.reprint(recordId));
    }

    @PostMapping("/record/query")
    @SaCheckPermission(ScmPrintPermission.RECORD_QUERY)
    public ResponseDTO<PageResult<ScmPrintRecordVO>> queryRecord(@Valid @RequestBody ScmPrintRecordQueryForm form) {
        return ResponseDTO.ok(scmPrintService.recordPage(form));
    }
}

package com.xsy.scm.print.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintRecordQueryForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateQueryForm;
import com.xsy.scm.print.domain.vo.ScmPrintDocumentTypeVO;
import com.xsy.scm.print.domain.vo.ScmPrintFieldCatalogVO;
import com.xsy.scm.print.domain.vo.ScmPrintRecordVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.domain.vo.ScmPrintTemplateVO;
import com.xsy.scm.print.permission.ScmPrintPermission;
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
 * 打印中心：模板维护 + 打印渲染 + 打印记录。
 *
 * <p>
 * <b>权限分两层</b>：
 * <ul>
 * <li>模板维护与记录查看用 {@code scm:print:*}（见 {@link ScmPrintPermission}）；</li>
 * <li>渲染与打印一张业务单据<b>不在这里授权</b>，它由数据源自己校验该单据的查看权
 * （采购单走 {@code PurchaseQueryService} 的采购员范围）—— 否则「模板维护权」会变成
 * 一条读到任意业务单据的旁路。</li>
 * </ul>
 *
 * <p>
 * 正式打印要 {@code Idempotency-Key}：一次提交重试不该产生两条「打过一次」的记录。
 * 该头不能标成必填 —— 缺失时由幂等服务抛业务码，而不是框架的 30001。
 */
@RestController
@RequestMapping("/scm/print")
@Tag(name = "SCM 打印中心")
@RequiredArgsConstructor
public class ScmPrintController {

    private final ScmPrintTemplateService scmPrintTemplateService;

    private final ScmPrintService scmPrintService;

    // ------------------------------------------------------------------
    // 模板
    // ------------------------------------------------------------------

    @PostMapping("/template/query")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<PageResult<ScmPrintTemplateVO>> queryTemplate(
            @Valid @RequestBody ScmPrintTemplateQueryForm form) {
        return ResponseDTO.ok(scmPrintTemplateService.queryPage(form));
    }

    /**
     * 字段目录：模板配置页据此渲染可选项，不硬编码字段清单。
     */
    @GetMapping("/template/catalog")
    @SaCheckPermission(ScmPrintPermission.TEMPLATE_QUERY)
    public ResponseDTO<ScmPrintFieldCatalogVO> catalog(@RequestParam("documentType") String documentType) {
        return ResponseDTO.ok(scmPrintTemplateService.catalog(documentType));
    }

    /**
     * 可配置打印的单据类型清单（前端类型下拉用）。
     */
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

    /**
     * 设为默认模板（同类型只保留一个默认）。
     */
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
    public ResponseDTO<String> deleteTemplate(@PathVariable("id") Long id,
            @RequestParam("version") Integer version) {
        scmPrintTemplateService.delete(id, version);
        return ResponseDTO.ok();
    }

    // ------------------------------------------------------------------
    // 渲染与打印
    // ------------------------------------------------------------------

    @GetMapping("/{documentType}/{businessId}/templates")
    public ResponseDTO<List<ScmPrintTemplateVO>> templateOptions(@PathVariable("documentType") String documentType,
            @PathVariable("businessId") Long businessId) {
        return ResponseDTO.ok(scmPrintService.templateOptions(documentType, businessId));
    }

    /**
     * 打印预览：只读，不计次、不留痕。
     *
     * <p>
     * {@code templateId} 为空时用该类型的默认模板。
     */
    @GetMapping("/{documentType}/{businessId}/preview")
    public ResponseDTO<ScmPrintRenderVO> preview(@PathVariable("documentType") String documentType,
            @PathVariable("businessId") Long businessId,
            @RequestParam(value = "templateId", required = false) Long templateId) {
        return ResponseDTO.ok(scmPrintService.preview(documentType, businessId, templateId));
    }

    /**
     * 正式打印：冻结模板版本、模型快照与版面快照，并返回这份冻结版面。
     */
    @PostMapping("/{documentType}/{businessId}/print")
    @OperateLog
    public ResponseDTO<ScmPrintRenderVO> print(@PathVariable("documentType") String documentType,
            @PathVariable("businessId") Long businessId, @Valid @RequestBody ScmPrintActionForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(scmPrintService.print(documentType, businessId, form, idempotencyKey));
    }

    /**
     * 历史重印：只读冻结快照，并按当前调用者的金额权限重新剔除。
     */
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

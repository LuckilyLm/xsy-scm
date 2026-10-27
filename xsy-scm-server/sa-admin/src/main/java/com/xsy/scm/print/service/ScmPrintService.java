package com.xsy.scm.print.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.constant.ScmPrintField;
import com.xsy.scm.print.dao.ScmPrintRecordDao;
import com.xsy.scm.print.dao.ScmPrintRecordQueryReadDao;
import com.xsy.scm.print.domain.entity.ScmPrintRecordEntity;
import com.xsy.scm.print.domain.entity.ScmPrintTemplateEntity;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintRecordQueryForm;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.xsy.scm.print.domain.vo.ScmPrintRecordVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderColumnVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderFieldVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.domain.vo.ScmPrintTemplateVO;
import com.xsy.scm.sorting.support.SortingAccess;
import com.xsy.scm.print.support.ScmPrintSource;
import com.xsy.scm.print.support.ScmPrintSourceProvider;
import cn.dev33.satoken.stp.StpUtil;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预览只读业务数据；正式打印保存当时的版面，历史重印直接用已保存内容，不重新生成。
 */
@Service
@RequiredArgsConstructor
public class ScmPrintService {

    private static final String PRINT_SCOPE = "SCM_PRINT";

    private final ScmPrintTemplateService scmPrintTemplateService;

    private final ScmPrintCustomerBindingService scmPrintCustomerBindingService;

    private final ScmPrintRenderService scmPrintRenderService;

    private final ScmPrintRecordDao scmPrintRecordDao;

    private final ScmPrintRecordQueryReadDao scmPrintRecordQueryReadDao;

    private final ScmIdempotencyService scmIdempotencyService;

    private final ScmDataScopeService scmDataScopeService;

    private final SortingAccess sortingAccess;

    /**
     * 打印预览（只读，不计次）。
     *
     * @param templateId
     *            为空时用该单据类型的默认模板
     */
    @Transactional(readOnly = true)
    public ScmPrintRenderVO preview(String documentType, Long businessId, Long templateId) {
        ScmPrintDocumentTypeEnum type = requireType(documentType);
        // 客户归属必须先过查看权再取：客户专属模板解析不能成为读取单据归属的旁路
        requireVisible(type, businessId);
        return render(type, businessId, template(type, businessId, templateId));
    }

    /**
     * 打印操作者只需业务查看权，不依赖模板管理权限。
     */
    @Transactional(readOnly = true)
    public List<ScmPrintTemplateVO> templateOptions(String documentType, Long businessId) {
        ScmPrintDocumentTypeEnum type = requireType(documentType);
        requireVisible(type, businessId);
        List<ScmPrintTemplateVO> options = new ArrayList<>(scmPrintTemplateService.enabledOptions(documentType));
        ScmPrintTemplateEntity bound = scmPrintCustomerBindingService.findBoundTemplate(documentType,
            scmPrintRenderService.provider(type).customerId(businessId));
        if (bound == null) {
            return options;
        }
        for (ScmPrintTemplateVO option : options) {
            if (option.getId().equals(bound.getId())) {
                option.setCustomerBound(true);
            }
        }
        // 客户专属模板排在最前：它就是「不指定模板」时实际会用的那一份
        options.sort((left, right) -> Boolean.compare(!left.getId().equals(bound.getId()),
            !right.getId().equals(bound.getId())));
        return options;
    }

    /**
     * 模板解析顺序：显式指定 → 客户专属模板 → 单据类型默认模板。
     *
     * <p>
     * 调用前必须已完成 {@link #requireVisible}，客户归属只能从带数据范围的业务查询取得。
     */
    private ScmPrintTemplateEntity template(ScmPrintDocumentTypeEnum type, Long businessId, Long templateId) {
        if (templateId != null) {
            return requireTypeMatch(type, scmPrintTemplateService.requireEnabled(templateId));
        }
        ScmPrintTemplateEntity bound = scmPrintCustomerBindingService.findBoundTemplate(type.name(),
            scmPrintRenderService.provider(type).customerId(businessId));
        if (bound != null) {
            return bound;
        }
        return scmPrintTemplateService.requireDefault(type.name());
    }

    private static ScmPrintTemplateEntity requireTypeMatch(ScmPrintDocumentTypeEnum type,
        ScmPrintTemplateEntity template) {
        if (!type.name().equals(template.getDocumentType())) {
            // 拿采购单的模板去打印发货单：字段完全不同，按「模板不存在」拒绝
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private ScmPrintRenderVO render(ScmPrintDocumentTypeEnum type, Long businessId, ScmPrintTemplateEntity template) {
        ScmPrintSourceProvider provider = scmPrintRenderService.provider(type);
        // 功能权限先判：数据源走的查询服务只做数据范围收窄，不判权限码。
        // 少了这一步，任何登录用户只要知道单据 id 就能把内容渲染出来。
        StpUtil.checkPermission(provider.queryPermission());
        ScmPrintSource source = provider.load(businessId);
        ScmPrintTemplateModel model = ScmPrintTemplateModel.fromMap(template.getModel());
        ScmPrintRenderVO render = scmPrintRenderService.render(type, source, model,
            scmPrintRenderService.amountVisible(type));
        render.setBusinessId(businessId);
        render.setTemplateId(template.getId());
        render.setTemplateCode(template.getTemplateCode());
        render.setTemplateName(template.getTemplateName());
        render.setTemplateVersion(template.getVersion());
        return render;
    }

    /**
     * 正式打印：保存模板版本、模板内容与版面内容，并返回这份结果。
     *
     * <p>
     * 要求幂等键：一次提交重试不该产生两条记录。同键重放直接返回首次的结果，拿到的是当时那张，而不是重新渲染的现在这张。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScmPrintRenderVO print(String documentType, Long businessId, ScmPrintActionForm form, String key) {
        ScmPrintDocumentTypeEnum type = requireType(documentType);
        requireVisible(type, businessId);
        var claim = scmIdempotencyService.claim(PRINT_SCOPE + ":" + documentType + ":" + businessId, key, form);
        if (claim.replay()) {
            ScmPrintRenderVO replay = scmIdempotencyService.replay(claim, ScmPrintRenderVO.class);
            maskAmount(type, replay);
            return replay;
        }

        ScmPrintTemplateEntity template = template(type, businessId, form.getTemplateId());
        ScmPrintRenderVO render = render(type, businessId, template);

        ScmPrintRecordEntity record = new ScmPrintRecordEntity();
        record.setDocumentType(render.getDocumentType());
        record.setBusinessId(businessId);
        record.setBusinessNo(render.getBusinessNo());
        record.setTemplateId(render.getTemplateId());
        record.setTemplateCode(render.getTemplateCode());
        record.setTemplateName(render.getTemplateName());
        record.setTemplateVersion(render.getTemplateVersion());
        record.setModelSnapshot(template.getModel());
        record.setDataSnapshot(ScmPrintRenderService.toDataSnapshot(render));
        record.setPrintedAt(OffsetDateTime.now());
        record.setPrintedBy(ScmOperator.current());
        scmPrintRecordDao.insertRecord(record);

        render.setFrozen(true);
        render.setRecordId(record.getId());
        render.setPrintedAt(record.getPrintedAt());
        render.setPrintedBy(record.getPrintedBy());
        scmIdempotencyService.complete(claim, "SCM_PRINT_RECORD", record.getId(), render);
        return render;
    }

    /**
     * 历史重印：只读当时保存的内容。
     *
     * <p>
     * 金额按当前调用者重新检查一遍权限：保存的内容里可能有原打印人有权限、当前人没权限的金额列， 直接回放等于用一条历史记录绕过金额权限。剔除时行里的值也一并删掉，只删列定义会把值留在行里。
     */
    @Transactional(readOnly = true)
    public ScmPrintRenderVO reprint(Long recordId) {
        ScmPrintRecordEntity record = scmPrintRecordDao.selectRecord(recordId);
        if (record == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.RECORD_NOT_FOUND);
        }
        ScmPrintDocumentTypeEnum type = requireType(record.getDocumentType());
        // 重印同样要该单据的查看权：保存的内容里有单据信息，只有记录查询权不该能读出它。
        requireVisible(type, record.getBusinessId());
        ScmPrintTemplateModel model = ScmPrintTemplateModel.fromMap(record.getModelSnapshot());

        ScmPrintRenderVO render = new ScmPrintRenderVO();
        render.setDocumentType(record.getDocumentType());
        render.setBusinessId(record.getBusinessId());
        render.setTemplateId(record.getTemplateId());
        render.setTemplateCode(record.getTemplateCode());
        render.setTemplateName(record.getTemplateName());
        render.setTemplateVersion(record.getTemplateVersion());
        render.setTitle(model.getTitle());
        render.setPaper(model.getPaper());
        render.setOrientation(model.getOrientation());
        render.setFooterNote(model.getFooterNote());
        render.setShowTotals(model.isShowTotals());
        ScmPrintRenderService.applyDataSnapshot(render, record.getDataSnapshot());
        maskAmount(type, render);
        render.setFrozen(true);
        render.setRecordId(record.getId());
        render.setPrintedAt(record.getPrintedAt());
        render.setPrintedBy(record.getPrintedBy());
        return render;
    }

    @Transactional(readOnly = true)
    public PageResult<ScmPrintRecordVO> recordPage(ScmPrintRecordQueryForm query) {
        if (query.getSortItemList() != null && !query.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR);
        }
        List<String> visibleTypes = java.util.Arrays.stream(ScmPrintDocumentTypeEnum.values())
            .filter(type -> ScmDataScopeService.hasPermission(scmPrintRenderService.provider(type).queryPermission()))
            .map(Enum::name).toList();
        if (visibleTypes.isEmpty()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<ScmPrintRecordVO> rows = scmPrintRecordQueryReadDao.queryPage(page, query, scmDataScopeService.resolve(),
            visibleTypes, sortingAccess.crossAssignee());
        rows.forEach(row -> row.setDocumentTypeLabel(ScmPrintDocumentTypeEnum.isSupported(row.getDocumentType())
            ? ScmPrintDocumentTypeEnum.valueOf(row.getDocumentType()).getLabel()
            : row.getDocumentType()));
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    private void requireVisible(ScmPrintDocumentTypeEnum type, Long businessId) {
        ScmPrintSourceProvider provider = scmPrintRenderService.provider(type);
        StpUtil.checkPermission(provider.queryPermission());
        provider.requireVisible(businessId);
    }

    private void maskAmount(ScmPrintDocumentTypeEnum type, ScmPrintRenderVO render) {
        if (!type.requiresMoneyPermission() || ScmDataScopeService.hasPermission(type.getMoneyPermission())) {
            return;
        }
        List<String> removed = new ArrayList<>();

        List<ScmPrintRenderFieldVO> header = new ArrayList<>();
        for (ScmPrintRenderFieldVO field : render.getHeaderFields()) {
            if (isMoney(type.headerField(field.getKey()))) {
                removed.add(field.getKey());
                continue;
            }
            header.add(field);
        }
        render.setHeaderFields(header);

        List<ScmPrintRenderColumnVO> columns = new ArrayList<>();
        for (ScmPrintRenderColumnVO column : render.getColumns()) {
            if (isMoney(type.column(column.getKey()))) {
                removed.add(column.getKey());
                continue;
            }
            columns.add(column);
        }
        render.setColumns(columns);

        List<ScmPrintRenderFieldVO> totals = new ArrayList<>();
        for (ScmPrintRenderFieldVO field : render.getTotals()) {
            if (isMoney(type.total(field.getKey()))) {
                removed.add(field.getKey());
                continue;
            }
            totals.add(field);
        }
        render.setTotals(totals);

        if (removed.isEmpty()) {
            return;
        }
        for (Map<String, String> row : render.getRows()) {
            for (String key : removed) {
                row.remove(key);
            }
        }
        Set<String> hidden = new LinkedHashSet<>(
            render.getHiddenFields() == null ? List.of() : render.getHiddenFields());
        hidden.addAll(removed);
        render.setHiddenFields(new ArrayList<>(hidden));
    }

    private static boolean isMoney(ScmPrintField field) {
        return field != null && field.money();
    }

    private static ScmPrintDocumentTypeEnum requireType(String documentType) {
        if (!ScmPrintDocumentTypeEnum.isSupported(documentType)) {
            throw new ScmBusinessException(ScmPrintErrorCode.DOCUMENT_TYPE_UNSUPPORTED);
        }
        return ScmPrintDocumentTypeEnum.valueOf(documentType);
    }
}

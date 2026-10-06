package com.xsy.scm.print.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.dao.ScmPrintTemplateDao;
import com.xsy.scm.print.domain.entity.ScmPrintTemplateEntity;
import com.xsy.scm.print.domain.form.ScmPrintTemplateForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateQueryForm;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.xsy.scm.print.domain.vo.ScmPrintDocumentTypeVO;
import com.xsy.scm.print.domain.vo.ScmPrintFieldCatalogVO;
import com.xsy.scm.print.domain.vo.ScmPrintTemplateVO;
import com.xsy.scm.print.support.ScmPrintTemplateValidator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 打印模板维护。
 *
 * <p>
 * 三条不变量：
 * <ul>
 * <li><b>同类型至多一个默认模板</b>：设默认是「清旧 + 设新」，两步在同一事务里； 该类型还没有任何默认时，新建的模板自动成为默认 —— 否则「不指定模板就打印」在只有一份 模板时会直接失败，而那正是最常见的情形。</li>
 * <li><b>模板类型不可改</b>：模型里的字段是相对类型校验过的，换类型等于把一份按 A 校验过的 配置挂到 B 上。要换就新建一份。</li>
 * <li><b>默认模板不可删</b>：删掉默认会让该类型的打印失去默认入口；先指定别的模板为默认。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScmPrintTemplateService {

    private final ScmPrintTemplateDao scmPrintTemplateDao;

    private final ScmPrintRenderService scmPrintRenderService;

    @Transactional(readOnly = true)
    public PageResult<ScmPrintTemplateVO> queryPage(ScmPrintTemplateQueryForm query) {
        var page = SmartPageUtil.convert2PageQuery(query);
        var rows = scmPrintTemplateDao.queryPage(page, query);
        return SmartPageUtil.convert2PageResult(page, rows.stream().map(ScmPrintTemplateService::toVO).toList());
    }

    public List<ScmPrintTemplateVO> enabledOptions(String documentType) {
        requireType(documentType);
        return scmPrintTemplateDao.enabledOptions(documentType).stream().map(ScmPrintTemplateService::toVO).toList();
    }

    /**
     * 可配置打印的单据类型清单（前端类型下拉据此渲染，不硬编码类型）。
     */
    @Transactional(readOnly = true)
    public List<ScmPrintDocumentTypeVO> documentTypes() {
        List<ScmPrintDocumentTypeVO> result = new ArrayList<>();
        for (ScmPrintDocumentTypeEnum type : ScmPrintDocumentTypeEnum.values()) {
            ScmPrintDocumentTypeVO vo = new ScmPrintDocumentTypeVO();
            vo.setDocumentType(type.name());
            vo.setDocumentTypeLabel(type.getLabel());
            vo.setAmountPermissionRequired(type.requiresMoneyPermission());
            vo.setAmountVisible(scmPrintRenderService.amountVisible(type));
            result.add(vo);
        }
        return result;
    }

    /**
     * 某单据类型的字段目录（模板配置页据此渲染可选项）。
     */
    @Transactional(readOnly = true)
    public ScmPrintFieldCatalogVO catalog(String documentType) {
        ScmPrintDocumentTypeEnum type = requireType(documentType);
        ScmPrintFieldCatalogVO catalog = new ScmPrintFieldCatalogVO();
        catalog.setDocumentType(type.name());
        catalog.setDocumentTypeLabel(type.getLabel());
        catalog.setHeaderFields(type.getHeaderFields());
        catalog.setColumns(type.getColumns());
        catalog.setTotals(type.getTotals());
        catalog.setAmountPermissionRequired(type.requiresMoneyPermission());
        catalog.setAmountVisible(scmPrintRenderService.amountVisible(type));
        return catalog;
    }

    @Transactional(readOnly = true)
    public ScmPrintTemplateVO detail(Long id) {
        ScmPrintTemplateEntity row = scmPrintTemplateDao.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ScmPrintTemplateForm form) {
        ScmPrintDocumentTypeEnum type = requireType(form.getDocumentType());
        ScmPrintTemplateModel model = ScmPrintTemplateValidator.validate(type,
                ScmPrintTemplateModel.fromMap(form.getModel()));
        requireCodeAvailable(type, form.getTemplateCode(), null);

        String operator = ScmOperator.current();
        // 该类型还没有默认模板时，新建的这一份直接成为默认：否则「不指定模板」的打印在
        // 只有一份模板时会失败，而那正是最常见的情形。
        boolean makeDefault = Boolean.TRUE.equals(form.getDefaultFlag())
                || scmPrintTemplateDao.selectDefault(type.name()) == null;
        if (makeDefault) {
            scmPrintTemplateDao.clearDefault(type.name(), null, operator);
        }

        ScmPrintTemplateEntity row = new ScmPrintTemplateEntity();
        row.setDocumentType(type.name());
        row.setTemplateCode(form.getTemplateCode().trim());
        row.setTemplateName(form.getTemplateName().trim());
        row.setDefaultFlag(makeDefault);
        row.setEnabledFlag(form.getEnabledFlag() == null || form.getEnabledFlag());
        row.setModel(model.toMap());
        row.setRemark(form.getRemark());
        row.setCreatedBy(operator);
        row.setUpdatedBy(operator);
        scmPrintTemplateDao.insertTemplate(row);
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ScmPrintTemplateForm form) {
        if (form.getId() == null || form.getVersion() == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        ScmPrintTemplateEntity existing = scmPrintTemplateDao.lock(form.getId());
        if (existing == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        if (!existing.getDocumentType().equals(form.getDocumentType())) {
            // 模型里的字段是相对原类型校验过的，换类型等于把按 A 校验的配置挂到 B 上
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        ScmPrintDocumentTypeEnum type = requireType(form.getDocumentType());
        ScmPrintTemplateModel model = ScmPrintTemplateValidator.validate(type,
                ScmPrintTemplateModel.fromMap(form.getModel()));
        requireCodeAvailable(type, form.getTemplateCode(), existing.getId());

        String operator = ScmOperator.current();
        boolean makeDefault = Boolean.TRUE.equals(form.getDefaultFlag());
        if (makeDefault) {
            scmPrintTemplateDao.clearDefault(type.name(), existing.getId(), operator);
        }
        if (scmPrintTemplateDao.updateTemplate(existing.getId(), form.getTemplateCode().trim(),
                form.getTemplateName().trim(), makeDefault, form.getEnabledFlag() == null || form.getEnabledFlag(),
                model.toMap(), form.getRemark(), form.getVersion(), operator) != 1) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 列表上的「设为默认」快捷动作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        ScmPrintTemplateEntity existing = scmPrintTemplateDao.lock(id);
        if (existing == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        String operator = ScmOperator.current();
        scmPrintTemplateDao.clearDefault(existing.getDocumentType(), existing.getId(), operator);
        if (scmPrintTemplateDao.setDefaultFlag(existing.getId(), true, operator) != 1) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        ScmPrintTemplateEntity existing = scmPrintTemplateDao.lock(id);
        if (existing == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(existing.getDefaultFlag())) {
            // 删掉默认会让该类型的「不指定模板打印」失去入口；先指定别的模板为默认
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_IN_USE);
        }
        if (scmPrintTemplateDao.softDelete(id, version, ScmOperator.current()) != 1) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 供打印侧按 id 取一份<b>启用中</b>的模板；不存在或已停用时抛「模板不存在」。
     *
     * <p>
     * 停用模板与不存在同样处理：停用就是「别再用了」，把停用报成另一个错误只会让调用方 多写一个分支。
     */
    @Transactional(readOnly = true)
    public ScmPrintTemplateEntity requireEnabled(Long templateId) {
        ScmPrintTemplateEntity row = scmPrintTemplateDao.selectById(templateId);
        if (row == null || Boolean.TRUE.equals(row.getDeleted()) || !Boolean.TRUE.equals(row.getEnabledFlag())) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        return row;
    }

    /**
     * 该类型的默认模板；没有时抛「模板不存在」（由调用方回答「先配一份模板」）。
     */
    @Transactional(readOnly = true)
    public ScmPrintTemplateEntity requireDefault(String documentType) {
        ScmPrintTemplateEntity row = scmPrintTemplateDao.selectDefault(documentType);
        if (row == null) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }
        return row;
    }

    private void requireCodeAvailable(ScmPrintDocumentTypeEnum type, String templateCode, Long selfId) {
        ScmPrintTemplateEntity existing = scmPrintTemplateDao.selectByCode(type.name(), templateCode.trim());
        if (existing != null && !existing.getId().equals(selfId)) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_CODE_DUPLICATED);
        }
    }

    private static ScmPrintDocumentTypeEnum requireType(String documentType) {
        if (!ScmPrintDocumentTypeEnum.isSupported(documentType)) {
            throw new ScmBusinessException(ScmPrintErrorCode.DOCUMENT_TYPE_UNSUPPORTED);
        }
        return ScmPrintDocumentTypeEnum.valueOf(documentType);
    }

    private static ScmPrintTemplateVO toVO(ScmPrintTemplateEntity row) {
        ScmPrintTemplateVO vo = new ScmPrintTemplateVO();
        vo.setId(row.getId());
        vo.setDocumentType(row.getDocumentType());
        vo.setDocumentTypeLabel(ScmPrintDocumentTypeEnum.isSupported(row.getDocumentType())
                ? ScmPrintDocumentTypeEnum.valueOf(row.getDocumentType()).getLabel()
                : row.getDocumentType());
        vo.setTemplateCode(row.getTemplateCode());
        vo.setTemplateName(row.getTemplateName());
        vo.setDefaultFlag(row.getDefaultFlag());
        vo.setEnabledFlag(row.getEnabledFlag());
        vo.setModel(ScmPrintTemplateModel.fromMap(row.getModel()));
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        vo.setUpdatedBy(row.getUpdatedBy());
        return vo;
    }
}

package com.xsy.scm.print.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.dao.ScmPrintCustomerTemplateDao;
import com.xsy.scm.print.domain.entity.ScmPrintCustomerTemplateEntity;
import com.xsy.scm.print.domain.entity.ScmPrintTemplateEntity;
import com.xsy.scm.print.domain.form.ScmPrintCustomerTemplateForm;
import com.xsy.scm.print.domain.vo.ScmPrintCustomerTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 客户专属模板绑定与解析。
 *
 * <p>
 * 绑定只改变「不指定模板」时选哪一份：显式传入的模板仍然优先；绑定模板停用或类型不符时回退到该类型默认模板， 不让一条过期配置阻断打印。
 */
@Service
@RequiredArgsConstructor
public class ScmPrintCustomerBindingService {

    private final ScmPrintCustomerTemplateDao scmPrintCustomerTemplateDao;

    private final ScmPrintTemplateService scmPrintTemplateService;

    private final CustomerService customerService;

    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public List<ScmPrintCustomerTemplateVO> query(String documentType, Long customerId) {
        String type = null;
        if (documentType != null && !documentType.isBlank()) {
            type = requireBindableType(documentType).name();
        }
        if (customerId != null && customerId <= 0) {
            throw new ScmBusinessException(ScmPrintErrorCode.CUSTOMER_BINDING_CUSTOMER_INVALID);
        }
        ScmValueScope customerScope = dataScopeService.resolve().getCustomerSellerScope();
        if (customerScope.isEmpty()) {
            return List.of();
        }
        // 指定客户时先按客户归属校验，避免用猜到的客户 ID 读取范围外的绑定
        if (customerId != null) {
            customerService.requireInCurrentScope(customerId);
        }
        List<ScmPrintCustomerTemplateVO> rows = scmPrintCustomerTemplateDao.queryBindings(type, customerId,
            customerScope);
        rows.forEach(row -> row.setDocumentTypeLabel(ScmPrintDocumentTypeEnum.isSupported(row.getDocumentType())
            ? ScmPrintDocumentTypeEnum.valueOf(row.getDocumentType()).getLabel()
            : row.getDocumentType()));
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(ScmPrintCustomerTemplateForm form) {
        ScmPrintDocumentTypeEnum type = requireBindableType(form.getDocumentType());
        if (!scmPrintCustomerTemplateDao.customerExists(form.getCustomerId())) {
            throw new ScmBusinessException(ScmPrintErrorCode.CUSTOMER_BINDING_CUSTOMER_INVALID);
        }
        // 绑定/换绑同样受客户归属范围约束
        customerService.requireInCurrentScope(form.getCustomerId());
        ScmPrintTemplateEntity template = scmPrintTemplateService.requireEnabled(form.getTemplateId());
        if (!type.name().equals(template.getDocumentType())) {
            // 绑定销售单却挂采购单模板：字段完全不同，按「模板不存在」拒绝
            throw new ScmBusinessException(ScmPrintErrorCode.TEMPLATE_NOT_FOUND);
        }

        String operator = ScmOperator.current();
        ScmPrintCustomerTemplateEntity existing = scmPrintCustomerTemplateDao.selectBinding(form.getCustomerId(),
            type.name());
        if (existing == null) {
            ScmPrintCustomerTemplateEntity row = new ScmPrintCustomerTemplateEntity();
            row.setCustomerId(form.getCustomerId());
            row.setDocumentType(type.name());
            row.setTemplateId(template.getId());
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            scmPrintCustomerTemplateDao.insertBinding(row);
            return;
        }
        if (!existing.getTemplateId().equals(template.getId())
            && scmPrintCustomerTemplateDao.updateBinding(existing.getId(), template.getId(), operator) != 1) {
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(String documentType, Long customerId) {
        ScmPrintDocumentTypeEnum type = requireBindableType(documentType);
        if (customerId == null || customerId <= 0) {
            throw new ScmBusinessException(ScmPrintErrorCode.CUSTOMER_BINDING_CUSTOMER_INVALID);
        }
        customerService.requireInCurrentScope(customerId);
        scmPrintCustomerTemplateDao.softDelete(customerId, type.name(), ScmOperator.current());
    }

    /**
     * 解析客户当前有效的专属模板；没有绑定、模板已停用或类型不符时返回 {@code null}，由调用方回退默认模板。
     */
    @Transactional(readOnly = true)
    public ScmPrintTemplateEntity findBoundTemplate(String documentType, Long customerId) {
        if (customerId == null || !ScmPrintDocumentTypeEnum.isSupported(documentType)
            || !ScmPrintDocumentTypeEnum.valueOf(documentType).supportsCustomerBinding()) {
            return null;
        }
        ScmPrintCustomerTemplateEntity binding = scmPrintCustomerTemplateDao.selectBinding(customerId, documentType);
        if (binding == null) {
            return null;
        }
        ScmPrintTemplateEntity template = scmPrintTemplateService.findEnabled(binding.getTemplateId());
        if (template == null || !documentType.equals(template.getDocumentType())) {
            return null;
        }
        return template;
    }

    private static ScmPrintDocumentTypeEnum requireBindableType(String documentType) {
        if (!ScmPrintDocumentTypeEnum.isSupported(documentType)) {
            throw new ScmBusinessException(ScmPrintErrorCode.DOCUMENT_TYPE_UNSUPPORTED);
        }
        ScmPrintDocumentTypeEnum type = ScmPrintDocumentTypeEnum.valueOf(documentType);
        if (!type.supportsCustomerBinding()) {
            throw new ScmBusinessException(ScmPrintErrorCode.CUSTOMER_BINDING_UNSUPPORTED);
        }
        return type;
    }
}

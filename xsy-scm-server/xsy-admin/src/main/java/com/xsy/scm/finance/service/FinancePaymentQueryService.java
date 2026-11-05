package com.xsy.scm.finance.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCustomerRefundMethodEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentMethodEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentDetailVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentQueryVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceQuerySupport;
import com.xsy.scm.base.common.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 付款的只读列表、详情与导出行选择。
 */
@Service
@RequiredArgsConstructor
public class FinancePaymentQueryService {

    private final FinancePaymentDao financePaymentDao;
    private final FinanceCounterpartySourceDao counterpartySourceDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogQueryService financeOperationLogQueryService;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinancePaymentQueryVO> query(FinancePaymentQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.query(form, () -> validateFilters(form),
            (page, range) -> financePaymentDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinancePaymentQueryVO> exportRows(FinancePaymentQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.exportRows(form, () -> validateFilters(form),
            (page, range) -> financePaymentDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public FinancePaymentDetailVO detail(Long paymentId) {
        FinancePaymentEntity entity = financePaymentDao.selectById(paymentId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (ScmFinanceCounterpartyTypeEnum.CUSTOMER.name().equals(entity.getCounterpartyType())) {
            FinanceCustomerFactDto customer = counterpartySourceDao.selectCustomer(entity.getCounterpartyId());
            Long sellerId = customer == null ? null : customer.getSellerId();
            if (!scope.getCustomerSellerScope().allows(sellerId)) {
                throw new ScmDataScopeException();
            }
        } else if (!ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(entity.getCounterpartyType())) {
            throw new ScmDataScopeException();
        }

        FinancePaymentQueryVO header = financePaymentDao.selectQueryById(paymentId);
        if (header == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
        }
        Long sourceId = entity.getReverseOfId() == null ? paymentId : entity.getReverseOfId();
        FinancePaymentQueryVO original = entity.getReverseOfId() == null
            ? null
            : financePaymentDao.selectQueryById(entity.getReverseOfId());
        FinancePaymentQueryVO reversal = entity.getReverseOfId() == null
            ? financePaymentDao.selectReversalByOriginal(paymentId)
            : null;
        List<FinanceWriteOffVO> writeOffs = writeOffQueryService
            .bySource(ScmFinanceWriteOffSourceTypeEnum.PAYMENT.name(), sourceId, scope);
        List<FinanceOperationLogVO> logs = new ArrayList<>(financeOperationLogQueryService
            .listByBusinessIds(ScmFinanceBusinessTypeEnum.PAYMENT.name(), List.of(sourceId)));
        logs.addAll(financeOperationLogQueryService.listByBusinessIds(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
            writeOffs.stream().map(FinanceWriteOffVO::getWriteOffId).toList()));
        logs.sort(
            Comparator.comparing(FinanceOperationLogVO::getCreatedAt).thenComparing(FinanceOperationLogVO::getId));

        FinancePaymentDetailVO detail = new FinancePaymentDetailVO();
        detail.setPayment(header);
        detail.setOriginal(original);
        detail.setReversal(reversal);
        detail.setWriteOffs(writeOffs);
        detail.setOperationLogs(logs);
        return detail;
    }

    private static void validateFilters(FinancePaymentQueryForm form) {
        if (form.getCounterpartyType() != null
            && !FinanceQuerySupport.enumContains(ScmFinanceCounterpartyTypeEnum.values(), form.getCounterpartyType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        // 付款方式按对手方分两组：供应商三值、客户退款四值。过滤是跨对手方的，
        // 因此两组都要放行 —— 只认一组会把另一组的值当成非法参数拒掉。
        if (form.getMethod() != null
            && !FinanceQuerySupport.enumContains(ScmFinancePaymentMethodEnum.values(), form.getMethod())
            && !FinanceQuerySupport.enumContains(ScmFinanceCustomerRefundMethodEnum.values(), form.getMethod())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSourceType() != null
            && !FinanceQuerySupport.enumContains(ScmFinancePaymentSourceTypeEnum.values(), form.getSourceType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getEntryType() != null
            && !FinanceQuerySupport.enumContains(ScmFinanceReverseEntryTypeEnum.values(), form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}

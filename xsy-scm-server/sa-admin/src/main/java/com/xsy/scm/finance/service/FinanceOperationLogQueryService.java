package com.xsy.scm.finance.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceOperationLogDao;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePayableSourceDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceOperationLogQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.permission.FinancePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** Finance audit history; both function permission and owner scope are checked per business type. */
@Service
@RequiredArgsConstructor
public class FinanceOperationLogQueryService {

    private final FinanceOperationLogDao financeOperationLogDao;
    private final FinanceReceivableDao financeReceivableDao;
    private final FinancePayableDao financePayableDao;
    private final FinanceReceiptDao financeReceiptDao;
    private final FinancePaymentDao financePaymentDao;
    private final FinanceWriteOffDao financeWriteOffDao;
    private final FinanceReceivableSourceDao financeReceivableSourceDao;
    private final FinancePayableSourceDao financePayableSourceDao;
    private final FinanceCounterpartySourceDao financeCounterpartySourceDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public List<FinanceOperationLogVO> query(FinanceOperationLogQueryForm form) {
        ScmFinanceBusinessTypeEnum businessType = businessType(form.getBusinessType());
        checkPermission(businessType);
        ScmDataScopeContext scope = dataScopeService.resolve();
        Long logBusinessId = requireBusinessVisible(businessType, form.getBusinessId(), scope);
        return financeOperationLogDao
                .selectList(new LambdaQueryWrapper<FinanceOperationLogEntity>()
                        .eq(FinanceOperationLogEntity::getBusinessType, businessType.name())
                        .eq(FinanceOperationLogEntity::getBusinessId, logBusinessId)
                        .orderByAsc(FinanceOperationLogEntity::getCreatedAt, FinanceOperationLogEntity::getId))
                .stream().map(FinanceOperationLogQueryService::toVO).toList();
    }

    private Long requireBusinessVisible(ScmFinanceBusinessTypeEnum type, Long businessId, ScmDataScopeContext scope) {
        switch (type) {
            case RECEIVABLE -> {
                FinanceReceivableEntity receivable = financeReceivableDao.selectActiveById(businessId);
                if (receivable == null) {
                    throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
                }
                require(scope.getOrderSellerScope()
                        .allows(financeReceivableSourceDao.selectOrderSellerId(receivable.getOrderId())));
                return businessId;
            }
            case PAYABLE -> {
                FinancePayableEntity payable = financePayableDao.selectActiveById(businessId);
                if (payable == null) {
                    throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
                }
                FinancePayableEntity normal = payable.getOriginalPayableId() == null
                        ? payable
                        : financePayableDao.selectActiveById(payable.getOriginalPayableId());
                if (normal == null || !scope.getPurchaserScope()
                        .allows(normal.getSourceId() == null
                                ? null
                                : financePayableSourceDao.selectPurchaserId(normal.getSourceId()))) {
                    throw new ScmDataScopeException();
                }
                return businessId;
            }
            case RECEIPT -> {
                FinanceReceiptEntity receipt = financeReceiptDao.selectById(businessId);
                if (receipt == null || Boolean.TRUE.equals(receipt.getDeleted())) {
                    throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
                }
                var customer = financeCounterpartySourceDao.selectCustomer(receipt.getCustomerId());
                require(scope.getCustomerSellerScope().allows(customer == null ? null : customer.getSellerId()));
                return ScmFinanceReverseEntryTypeEnum.REVERSE.name().equals(receipt.getEntryType())
                        ? receipt.getReverseOfId()
                        : businessId;
            }
            case PAYMENT -> {
                FinancePaymentEntity payment = financePaymentDao.selectById(businessId);
                if (payment == null || Boolean.TRUE.equals(payment.getDeleted())) {
                    throw new ScmBusinessException(FinanceErrorCode.PAYMENT_NOT_FOUND);
                }
                if (ScmFinanceCounterpartyTypeEnum.CUSTOMER.name().equals(payment.getCounterpartyType())) {
                    var customer = financeCounterpartySourceDao.selectCustomer(payment.getCounterpartyId());
                    require(scope.getCustomerSellerScope().allows(customer == null ? null : customer.getSellerId()));
                } else if (!ScmFinanceCounterpartyTypeEnum.SUPPLIER.name().equals(payment.getCounterpartyType())) {
                    throw new ScmDataScopeException();
                }
                return ScmFinanceReverseEntryTypeEnum.REVERSE.name().equals(payment.getEntryType())
                        ? payment.getReverseOfId()
                        : businessId;
            }
            case WRITE_OFF -> {
                FinanceWriteOffEntity writeOff = financeWriteOffDao.selectActiveById(businessId);
                if (writeOff == null) {
                    throw new ScmBusinessException(FinanceErrorCode.WRITE_OFF_NOT_FOUND);
                }
                if (ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name().equals(writeOff.getTargetType())) {
                    FinanceReceivableEntity receivable = financeReceivableDao.selectActiveById(writeOff.getTargetId());
                    if (receivable == null) {
                        throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
                    }
                    require(scope.getOrderSellerScope()
                            .allows(financeReceivableSourceDao.selectOrderSellerId(receivable.getOrderId())));
                } else if (ScmFinanceWriteOffTargetTypeEnum.PAYABLE.name().equals(writeOff.getTargetType())) {
                    FinancePayableEntity payable = financePayableDao.selectActiveById(writeOff.getTargetId());
                    if (payable == null) {
                        throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
                    }
                    require(scope.getPurchaserScope()
                            .allows(payable.getSourceId() == null
                                    ? null
                                    : financePayableSourceDao.selectPurchaserId(payable.getSourceId())));
                } else {
                    throw new ScmBusinessException(VALIDATION_ERROR);
                }
                return ScmFinanceReverseEntryTypeEnum.REVERSE.name().equals(writeOff.getEntryType())
                        ? writeOff.getReverseOfId()
                        : businessId;
            }
        }
        throw new ScmBusinessException(VALIDATION_ERROR);
    }

    private static ScmFinanceBusinessTypeEnum businessType(String raw) {
        if (raw != null) {
            for (ScmFinanceBusinessTypeEnum candidate : ScmFinanceBusinessTypeEnum.values()) {
                if (candidate.name().equals(raw.trim())) {
                    return candidate;
                }
            }
        }
        throw new ScmBusinessException(VALIDATION_ERROR);
    }

    private static void checkPermission(ScmFinanceBusinessTypeEnum type) {
        switch (type) {
            case RECEIVABLE -> StpUtil.checkPermission(FinancePermission.RECEIVABLE_QUERY);
            case PAYABLE -> StpUtil.checkPermission(FinancePermission.PAYABLE_QUERY);
            case RECEIPT -> StpUtil.checkPermission(FinancePermission.RECEIPT_QUERY);
            case PAYMENT -> StpUtil.checkPermission(FinancePermission.PAYMENT_QUERY);
            case WRITE_OFF -> StpUtil.checkPermission(FinancePermission.WRITE_OFF_QUERY);
        }
    }

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new ScmDataScopeException();
        }
    }

    private static FinanceOperationLogVO toVO(FinanceOperationLogEntity entity) {
        FinanceOperationLogVO vo = new FinanceOperationLogVO();
        vo.setId(entity.getId());
        vo.setBusinessType(entity.getBusinessType());
        vo.setBusinessId(entity.getBusinessId());
        vo.setOperationType(entity.getOperationType());
        vo.setOperator(entity.getOperator());
        vo.setReason(entity.getReason());
        vo.setBeforeData(entity.getBeforeData());
        vo.setAfterData(entity.getAfterData());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}

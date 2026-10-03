package com.xsy.scm.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.common.time.ScmDateTimeRangeResolver;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceiptMethodEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceOperationLogDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptDetailVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceExportGuard;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** Read-only receipt query, detail and export row selection. */
@Service
@RequiredArgsConstructor
public class FinanceReceiptQueryService {

    private final FinanceReceiptDao financeReceiptDao;
    private final FinanceCounterpartySourceDao counterpartySourceDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogDao financeOperationLogDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinanceReceiptQueryVO> query(FinanceReceiptQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, financeReceiptDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinanceReceiptQueryVO> exportRows(FinanceReceiptQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return List.of();
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        return FinanceExportGuard.exportRows(pageSize -> {
            Page<?> page = page(form, pageSize);
            page.setSearchCount(false);
            return financeReceiptDao.queryPage(page, form, scope, range);
        });
    }

    @Transactional(readOnly = true)
    public FinanceReceiptDetailVO detail(Long receiptId) {
        FinanceReceiptEntity entity = financeReceiptDao.selectById(receiptId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        FinanceCustomerFactDto customer = counterpartySourceDao.selectCustomer(entity.getCustomerId());
        Long sellerId = customer == null ? null : customer.getSellerId();
        if (!scope.getCustomerSellerScope().allows(sellerId)) {
            throw new ScmDataScopeException();
        }

        FinanceReceiptQueryVO header = financeReceiptDao.selectQueryById(receiptId);
        if (header == null) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIPT_NOT_FOUND);
        }
        Long sourceId = entity.getReverseOfId() == null ? receiptId : entity.getReverseOfId();
        FinanceReceiptQueryVO original = entity.getReverseOfId() == null
                ? null
                : financeReceiptDao.selectQueryById(entity.getReverseOfId());
        FinanceReceiptQueryVO reversal = entity.getReverseOfId() == null
                ? financeReceiptDao.selectReversalByOriginal(receiptId)
                : null;
        List<FinanceWriteOffVO> writeOffs = writeOffQueryService
                .bySource(ScmFinanceWriteOffSourceTypeEnum.RECEIPT.name(), sourceId, scope);
        List<FinanceOperationLogVO> logs = new ArrayList<>(operationLogs(sourceId));
        logs.addAll(operationLogs(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
                writeOffs.stream().map(FinanceWriteOffVO::getWriteOffId).toList()));
        logs.sort(
                Comparator.comparing(FinanceOperationLogVO::getCreatedAt).thenComparing(FinanceOperationLogVO::getId));

        FinanceReceiptDetailVO detail = new FinanceReceiptDetailVO();
        detail.setReceipt(header);
        detail.setOriginal(original);
        detail.setReversal(reversal);
        detail.setWriteOffs(writeOffs);
        detail.setOperationLogs(logs);
        return detail;
    }

    private List<FinanceOperationLogVO> operationLogs(Long receiptId) {
        return operationLogs(ScmFinanceBusinessTypeEnum.RECEIPT.name(), List.of(receiptId));
    }

    private List<FinanceOperationLogVO> operationLogs(String businessType, List<Long> businessIds) {
        if (businessIds.isEmpty()) {
            return List.of();
        }
        return financeOperationLogDao
                .selectList(new LambdaQueryWrapper<FinanceOperationLogEntity>()
                        .eq(FinanceOperationLogEntity::getBusinessType, businessType)
                        .in(FinanceOperationLogEntity::getBusinessId, businessIds)
                        .orderByAsc(FinanceOperationLogEntity::getCreatedAt, FinanceOperationLogEntity::getId))
                .stream().map(entity -> {
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
                }).toList();
    }

    private static void rejectClientSort(FinanceReceiptQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static void normalizePage(FinanceReceiptQueryForm form) {
        if (form.getPageNum() == null || form.getPageNum() < 1) {
            form.setPageNum(1L);
        }
        if (form.getPageSize() == null || form.getPageSize() < 1) {
            form.setPageSize(20L);
        }
    }

    private static void validateFilters(FinanceReceiptQueryForm form) {
        if (form.getMethod() != null && !enumContains(ScmFinanceReceiptMethodEnum.values(), form.getMethod())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getEntryType() != null
                && !enumContains(ScmFinanceReverseEntryTypeEnum.values(), form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static boolean enumContains(Enum<?>[] values, String value) {
        return java.util.Arrays.stream(values).anyMatch(candidate -> candidate.name().equals(value));
    }

    private static Page<?> page(FinanceReceiptQueryForm form, long pageSize) {
        form.setPageNum(1L);
        form.setPageSize(pageSize);
        return SmartPageUtil.convert2PageQuery(form);
    }
}

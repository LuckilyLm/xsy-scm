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
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinanceOperationLogDao;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePayableItemDao;
import com.xsy.scm.finance.dao.FinancePayableSourceDao;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableItemEntity;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinancePayableDetailVO;
import com.xsy.scm.finance.domain.vo.FinancePayableItemVO;
import com.xsy.scm.finance.domain.vo.FinancePayableVO;
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

/** Read-only AP query, detail and export row selection. */
@Service
@RequiredArgsConstructor
public class FinancePayableQueryService {

    private static final java.util.Set<String> SETTLE_STATES = java.util.Set.of("OPEN", "PARTIAL", "SETTLED");

    private static final java.util.Set<String> ENTRY_TYPES = java.util.Set.of(ScmFinanceEntryTypeEnum.NORMAL.name(),
            ScmFinanceEntryTypeEnum.RED.name());

    private final FinancePayableDao financePayableDao;
    private final FinancePayableItemDao financePayableItemDao;
    private final FinancePayableSourceDao financePayableSourceDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogDao financeOperationLogDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinancePayableVO> query(FinancePayableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getPurchaserScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, financePayableDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinancePayableVO> exportRows(FinancePayableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getPurchaserScope().isEmpty()) {
            return List.of();
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        return FinanceExportGuard.exportRows(pageSize -> {
            Page<?> page = page(form, pageSize);
            page.setSearchCount(false);
            return financePayableDao.queryPage(page, form, scope, range);
        });
    }

    @Transactional(readOnly = true)
    public FinancePayableDetailVO detail(Long payableId) {
        FinancePayableEntity entity = financePayableDao.selectActiveById(payableId);
        if (entity == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
        }
        FinancePayableEntity normal = entity.getOriginalPayableId() == null
                ? entity
                : financePayableDao.selectActiveById(entity.getOriginalPayableId());
        if (normal == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        Long purchaserId = normal.getSourceId() == null
                ? null
                : financePayableSourceDao.selectPurchaserId(normal.getSourceId());
        if (!scope.getPurchaserScope().allows(purchaserId)) {
            throw new ScmDataScopeException();
        }

        FinancePayableVO header = financePayableDao.selectSummaryById(payableId);
        if (header == null) {
            throw new ScmBusinessException(FinanceErrorCode.PAYABLE_NOT_FOUND);
        }
        List<FinancePayableItemVO> items = financePayableItemDao.selectByPayableId(payableId).stream()
                .map(FinancePayableQueryService::itemVO).toList();
        List<FinancePayableVO> redEntries = entity.getOriginalPayableId() == null
                ? financePayableDao.selectRedEntriesByOriginal(payableId)
                : List.of();
        FinancePayableVO original = entity.getOriginalPayableId() == null
                ? null
                : financePayableDao.selectSummaryById(entity.getOriginalPayableId());
        List<FinanceWriteOffVO> writeOffs = ScmFinanceEntryTypeEnum.NORMAL.name().equals(entity.getEntryType())
                ? writeOffQueryService.byTarget(ScmFinanceWriteOffTargetTypeEnum.PAYABLE.name(), payableId, scope)
                : List.of();

        List<Long> payableLogIds = new ArrayList<>();
        payableLogIds.add(payableId);
        redEntries.forEach(red -> payableLogIds.add(red.getPayableId()));
        if (original != null) {
            payableLogIds.add(original.getPayableId());
        }
        List<FinanceOperationLogVO> logs = new ArrayList<>(
                operationLogs(ScmFinanceBusinessTypeEnum.PAYABLE.name(), payableLogIds));
        logs.addAll(operationLogs(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
                writeOffs.stream().map(FinanceWriteOffVO::getWriteOffId).toList()));
        logs.sort(
                Comparator.comparing(FinanceOperationLogVO::getCreatedAt).thenComparing(FinanceOperationLogVO::getId));

        FinancePayableDetailVO detail = new FinancePayableDetailVO();
        detail.setPayable(header);
        detail.setItems(items);
        detail.setRedEntries(redEntries);
        detail.setOriginalPayable(original);
        detail.setWriteOffs(writeOffs);
        detail.setOperationLogs(logs);
        return detail;
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
                .stream().map(FinancePayableQueryService::logVO).toList();
    }

    private static FinancePayableItemVO itemVO(FinancePayableItemEntity item) {
        FinancePayableItemVO vo = new FinancePayableItemVO();
        vo.setPayableItemId(item.getId());
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId());
        vo.setPurchaseOrderItemId(item.getPurchaseOrderItemId());
        vo.setSkuId(item.getSkuId());
        vo.setSkuName(item.getSkuNameSnapshot());
        vo.setUnit(item.getUnitSnapshot());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        return vo;
    }

    private static FinanceOperationLogVO logVO(FinanceOperationLogEntity entity) {
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

    private static void rejectClientSort(FinancePayableQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static void normalizePage(FinancePayableQueryForm form) {
        if (form.getPageNum() == null || form.getPageNum() < 1) {
            form.setPageNum(1L);
        }
        if (form.getPageSize() == null || form.getPageSize() < 1) {
            form.setPageSize(20L);
        }
    }

    private static void validateFilters(FinancePayableQueryForm form) {
        if (form.getEntryType() != null && !ENTRY_TYPES.contains(form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSettleState() != null && !SETTLE_STATES.contains(form.getSettleState())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static Page<?> page(FinancePayableQueryForm form, long pageSize) {
        form.setPageNum(1L);
        form.setPageSize(pageSize);
        return SmartPageUtil.convert2PageQuery(form);
    }
}

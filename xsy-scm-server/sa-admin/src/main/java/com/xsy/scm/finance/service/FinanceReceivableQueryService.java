package com.xsy.scm.finance.service;

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
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableItemDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableItemEntity;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableDetailVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableItemVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceExportGuard;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** Read-only AR query, detail and export row selection. */
@Service
@RequiredArgsConstructor
public class FinanceReceivableQueryService {

    private static final java.util.Set<String> SETTLE_STATES = java.util.Set.of("OPEN", "PARTIAL", "SETTLED");

    private static final java.util.Set<String> ENTRY_TYPES = java.util.Set.of(ScmFinanceEntryTypeEnum.NORMAL.name(),
            ScmFinanceEntryTypeEnum.RED.name());

    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceReceivableItemDao financeReceivableItemDao;
    private final FinanceReceivableSourceDao financeReceivableSourceDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogDao financeOperationLogDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinanceReceivableVO> query(FinanceReceivableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, financeReceivableDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinanceReceivableVO> exportRows(FinanceReceivableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty()) {
            return List.of();
        }
        rejectClientSort(form);
        normalizePage(form);
        validateFilters(form);
        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        return FinanceExportGuard.exportRows(pageSize -> {
            Page<?> page = page(form, pageSize);
            page.setSearchCount(false);
            return financeReceivableDao.queryPage(page, form, scope, range);
        });
    }

    @Transactional(readOnly = true)
    public FinanceReceivableDetailVO detail(Long receivableId) {
        FinanceReceivableEntity entity = financeReceivableDao.selectActiveById(receivableId);
        if (entity == null) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        Long sellerId = financeReceivableSourceDao.selectOrderSellerId(entity.getOrderId());
        if (!scope.getOrderSellerScope().allows(sellerId)) {
            throw new ScmDataScopeException();
        }

        FinanceReceivableVO header = financeReceivableDao.selectSummaryById(receivableId);
        if (header == null) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
        }
        List<FinanceReceivableItemVO> items = financeReceivableItemDao.selectByReceivableId(receivableId).stream()
                .map(FinanceReceivableQueryService::itemVO).toList();
        List<FinanceReceivableVO> redEntries = ScmFinanceEntryTypeEnum.NORMAL.name().equals(entity.getEntryType())
                ? financeReceivableDao.selectRedEntriesByOriginal(receivableId)
                : List.of();
        FinanceReceivableVO original = entity.getOriginalReceivableId() == null
                ? null
                : financeReceivableDao.selectSummaryById(entity.getOriginalReceivableId());
        List<FinanceWriteOffVO> writeOffs = ScmFinanceEntryTypeEnum.NORMAL.name().equals(entity.getEntryType())
                ? writeOffQueryService.byTarget(ScmFinanceWriteOffTargetTypeEnum.RECEIVABLE.name(), receivableId, scope)
                : List.of();

        List<Long> receivableLogIds = new ArrayList<>();
        receivableLogIds.add(receivableId);
        redEntries.forEach(red -> receivableLogIds.add(red.getReceivableId()));
        if (original != null) {
            receivableLogIds.add(original.getReceivableId());
        }
        List<FinanceOperationLogVO> logs = new ArrayList<>(
                operationLogs(ScmFinanceBusinessTypeEnum.RECEIVABLE.name(), receivableLogIds));
        logs.addAll(operationLogs(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
                writeOffs.stream().map(FinanceWriteOffVO::getWriteOffId).toList()));
        logs.sort(
                Comparator.comparing(FinanceOperationLogVO::getCreatedAt).thenComparing(FinanceOperationLogVO::getId));

        FinanceReceivableDetailVO detail = new FinanceReceivableDetailVO();
        detail.setReceivable(header);
        detail.setItems(items);
        detail.setRedEntries(redEntries);
        detail.setOriginalReceivable(original);
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
                .stream().map(FinanceReceivableQueryService::logVO).toList();
    }

    private static FinanceReceivableItemVO itemVO(FinanceReceivableItemEntity item) {
        FinanceReceivableItemVO vo = new FinanceReceivableItemVO();
        vo.setReceivableItemId(item.getId());
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId());
        vo.setOrderItemId(item.getOrderItemId());
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

    private static void rejectClientSort(FinanceReceivableQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static void normalizePage(FinanceReceivableQueryForm form) {
        if (form.getPageNum() == null || form.getPageNum() < 1) {
            form.setPageNum(1L);
        }
        if (form.getPageSize() == null || form.getPageSize() < 1) {
            form.setPageSize(20L);
        }
    }

    private static void validateFilters(FinanceReceivableQueryForm form) {
        if (form.getEntryType() != null && !ENTRY_TYPES.contains(form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSettleState() != null && !SETTLE_STATES.contains(form.getSettleState())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static Page<?> page(FinanceReceivableQueryForm form, long pageSize) {
        form.setPageNum(1L);
        form.setPageSize(pageSize);
        return SmartPageUtil.convert2PageQuery(form);
    }
}

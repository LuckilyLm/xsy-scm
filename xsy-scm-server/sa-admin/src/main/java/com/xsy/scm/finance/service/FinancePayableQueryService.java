package com.xsy.scm.finance.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceSettleStateEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePayableItemDao;
import com.xsy.scm.finance.dao.FinancePayableSourceDao;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePayableItemEntity;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinancePayableDetailVO;
import com.xsy.scm.finance.domain.vo.FinancePayableItemVO;
import com.xsy.scm.finance.domain.vo.FinancePayableVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceQuerySupport;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 应付的只读列表、详情与导出行选择。
 */
@Service
@RequiredArgsConstructor
public class FinancePayableQueryService {

    private static final java.util.Set<String> SETTLE_STATES = java.util.Set.of(ScmFinanceSettleStateEnum.OPEN.name(),
        ScmFinanceSettleStateEnum.PARTIAL.name(), ScmFinanceSettleStateEnum.SETTLED.name());

    private static final java.util.Set<String> ENTRY_TYPES = java.util.Set.of(ScmFinanceEntryTypeEnum.NORMAL.name(),
        ScmFinanceEntryTypeEnum.RED.name());

    private final FinancePayableDao financePayableDao;
    private final FinancePayableItemDao financePayableItemDao;
    private final FinancePayableSourceDao financePayableSourceDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogQueryService financeOperationLogQueryService;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinancePayableVO> query(FinancePayableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.query(form, scope.getPurchaserScope(), () -> validateFilters(form),
            (page, range) -> financePayableDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinancePayableVO> exportRows(FinancePayableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.exportRows(form, scope.getPurchaserScope(), () -> validateFilters(form),
            (page, range) -> financePayableDao.queryPage(page, form, scope, range));
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
            : financePayableSourceDao.selectPurchaserByOrderId(normal.getPurchaseOrderId());
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
        List<FinanceOperationLogVO> logs = new ArrayList<>(financeOperationLogQueryService
            .listByBusinessIds(ScmFinanceBusinessTypeEnum.PAYABLE.name(), payableLogIds));
        logs.addAll(financeOperationLogQueryService.listByBusinessIds(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
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

    private static void validateFilters(FinancePayableQueryForm form) {
        if (form.getEntryType() != null && !ENTRY_TYPES.contains(form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSettleState() != null && !SETTLE_STATES.contains(form.getSettleState())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}

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
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableItemDao;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableItemEntity;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableDetailVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableItemVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
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
 * 应收的只读列表、详情与导出行选择。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableQueryService {

    private static final java.util.Set<String> SETTLE_STATES = java.util.Set.of(ScmFinanceSettleStateEnum.OPEN.name(),
        ScmFinanceSettleStateEnum.PARTIAL.name(), ScmFinanceSettleStateEnum.SETTLED.name());

    private static final java.util.Set<String> ENTRY_TYPES = java.util.Set.of(ScmFinanceEntryTypeEnum.NORMAL.name(),
        ScmFinanceEntryTypeEnum.RED.name());

    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceReceivableItemDao financeReceivableItemDao;
    private final FinanceWriteOffQueryService writeOffQueryService;
    private final FinanceOperationLogQueryService financeOperationLogQueryService;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinanceReceivableVO> query(FinanceReceivableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.query(form, scope.getOrderSellerScope(), () -> validateFilters(form),
            (page, range) -> financeReceivableDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public List<FinanceReceivableVO> exportRows(FinanceReceivableQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return FinanceQuerySupport.exportRows(form, scope.getOrderSellerScope(), () -> validateFilters(form),
            (page, range) -> financeReceivableDao.queryPage(page, form, scope, range));
    }

    @Transactional(readOnly = true)
    public FinanceReceivableDetailVO detail(Long receivableId) {
        FinanceReceivableEntity entity = financeReceivableDao.selectActiveById(receivableId);
        if (entity == null) {
            throw new ScmBusinessException(FinanceErrorCode.RECEIVABLE_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        // 独立退货没有订单，详情必须与列表一样回落到客户负责人，不能把空订单误判为无权。
        Long sellerId = financeReceivableDao.selectSellerId(receivableId);
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
        List<FinanceOperationLogVO> logs = new ArrayList<>(financeOperationLogQueryService
            .listByBusinessIds(ScmFinanceBusinessTypeEnum.RECEIVABLE.name(), receivableLogIds));
        logs.addAll(financeOperationLogQueryService.listByBusinessIds(ScmFinanceBusinessTypeEnum.WRITE_OFF.name(),
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
        vo.setGrossAmount(item.getGrossAmount());
        vo.setDiscountAmount(item.getDiscountAmount());
        vo.setAmount(item.getAmount());
        return vo;
    }

    private static void validateFilters(FinanceReceivableQueryForm form) {
        if (form.getEntryType() != null && !ENTRY_TYPES.contains(form.getEntryType())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSettleState() != null && !SETTLE_STATES.contains(form.getSettleState())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}

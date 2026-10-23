package com.xsy.scm.inventory.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.domain.form.InventoryBalanceQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryBalanceVO;
import com.xsy.scm.report.support.ScmReportAccess;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND;

/**
 * 只读查询库存余额，不修改库存。未指定仓库时不自动选择默认仓库，由前端处理。排序按服务端固定规则执行，拒绝客户端自定义排序，避免多表字段冲突。
 */
@Service
@RequiredArgsConstructor
public class InventoryBalanceQueryService {

    private final InventoryBalanceDao inventoryBalanceDao;

    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<InventoryBalanceVO> query(InventoryBalanceQueryForm form) {
        rejectClientSort(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<InventoryBalanceVO> list = inventoryBalanceDao.queryPage(page, form, scope.getWarehouseScope());
        ScmReportAccess.maskCost(list, scope.isCostVisible(), InventoryBalanceQueryService::clearCost);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 余额详情；不存在 → 40486，仓库未授权 → 无权限（不按「不存在」回答，否则等于把存在性告诉对方）。
     */
    @Transactional(readOnly = true)
    public InventoryBalanceVO detail(Long balanceId) {
        InventoryBalanceVO vo = balanceId == null ? null : inventoryBalanceDao.detail(balanceId);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_BALANCE_NOT_FOUND);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (!scope.getWarehouseScope().allows(vo.getWarehouseId())) {
            throw new ScmDataScopeException();
        }
        ScmReportAccess.maskCost(List.of(vo), scope.isCostVisible(), InventoryBalanceQueryService::clearCost);
        return vo;
    }

    /**
     * 均价与账面金额同源（都来自 {@code avg_cost}），无成本权限时一并置为 null；置成 0 会被读成「这批货没有成本」，这是一个结论，而不是无权知道。
     */
    private static void clearCost(InventoryBalanceVO vo) {
        vo.setAvgCost(null);
        vo.setAmount(null);
    }

    /**
     * 见类注释：join 查询不接受客户端排序。
     */
    static void rejectClientSort(PageParam form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}

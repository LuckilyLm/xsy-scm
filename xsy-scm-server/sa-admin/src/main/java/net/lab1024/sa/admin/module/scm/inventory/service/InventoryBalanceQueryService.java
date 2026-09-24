package net.lab1024.sa.admin.module.scm.inventory.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryBalanceQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryBalanceVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND;

/**
 * 库存余额只读查询（W6 Target Design §10.1）。
 *
 * <p><b>只读</b>：本类不写任何表，只读事务。余额的唯一写入路径是
 * {@link InventoryCommandService}（收货确认同事务）。
 *
 * <p><b>Q12 后端不做隐式默认</b>：{@code warehouseId} 为空即不过滤。
 * 「恰好只有一个启用仓库时默认带出该仓库」是**前端**行为（余额页加载时联
 * {@code GET /scm/warehouse/list} 判定），服务端不会替调用方选仓库。
 *
 * <p><b>为什么不接受客户端排序</b>：本查询是 join（余额 + 仓库 + SKU + 商品），
 * 客户端传来的裸列名（如 {@code updated_at}）在四张表里都存在，交给框架拼 ORDER BY
 * 会直接产生歧义列错误。W6-1 的排序语义是固定的业务序
 * （余额：{@code updated_at DESC}），因此这里对 {@code sortItemList} **显式报错**
 * 而不是静默忽略 —— 静默忽略会让前端以为排序生效了。
 */
@Service
@RequiredArgsConstructor
public class InventoryBalanceQueryService {

    private final InventoryBalanceDao balanceDao;

    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<InventoryBalanceVO> query(InventoryBalanceQueryForm form) {
        rejectClientSort(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<InventoryBalanceVO> list = balanceDao.queryPage(page, form, scope.getWarehouseScope());
        ScmReportAccess.maskCost(list, scope.isCostVisible(), InventoryBalanceQueryService::clearCost);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 余额详情；不存在 → 40486，仓库未授权 → 无权限（不按「不存在」回答，否则等于把存在性告诉对方）。
     */
    @Transactional(readOnly = true)
    public InventoryBalanceVO detail(Long id) {
        InventoryBalanceVO vo = id == null ? null : balanceDao.detail(id);
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
     * 均价与账面金额同源（都来自 {@code avg_cost}），无成本权限时一并抹成 null；
     * 抹成 0 会被读成「这批货没有成本」，那是一个事实，不是无权知道。
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

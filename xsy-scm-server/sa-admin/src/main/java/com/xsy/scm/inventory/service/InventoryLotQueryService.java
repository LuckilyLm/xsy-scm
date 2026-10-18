package com.xsy.scm.inventory.service;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.dao.InventoryLotQueryDao;
import com.xsy.scm.inventory.domain.form.InventoryLotBalanceQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryLotMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryLotBalanceVO;
import com.xsy.scm.inventory.domain.vo.InventoryLotMovementVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批次库存与批次流水的只读查询。
 *
 * <p>
 * 只读聚合：数量直接取批次子账，不在查询层二次计算，也不回表重算总账；仓库范围与余额查询同源，空范围失败关闭。
 */
@Service
@RequiredArgsConstructor
public class InventoryLotQueryService {

    private final InventoryLotQueryDao inventoryLotQueryDao;

    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<InventoryLotBalanceVO> queryBalance(InventoryLotBalanceQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<InventoryLotBalanceVO> list = inventoryLotQueryDao.queryBalancePage(page, form, scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }

    @Transactional(readOnly = true)
    public PageResult<InventoryLotMovementVO> queryMovement(InventoryLotMovementQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<InventoryLotMovementVO> list = inventoryLotQueryDao.queryMovementPage(page, form,
                scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }
}

package com.xsy.scm.inventory.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import com.xsy.scm.inventory.dao.InventoryWarningThresholdDao;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningThresholdVO;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_NOT_FOUND;

/**
 * 只读查询预警阈值和当前库存预警。 预警状态由可用量与阈值计算，不单独存库。 SQL 的状态筛选与 Java 的状态计算必须保持一致。
 */
@Service
@RequiredArgsConstructor
public class InventoryWarningQueryService {

    private final InventoryWarningThresholdDao inventoryWarningThresholdDao;

    private final ScmDataScopeService dataScopeService;

    /**
     * 阈值配置分页（联仓库 / SKU / 商品取展示字段）。
     */
    public PageResult<InventoryWarningThresholdVO> queryThresholdPage(InventoryWarningThresholdQueryForm query) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryWarningThresholdVO> list = inventoryWarningThresholdDao.queryPage(page, query,
            scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 阈值配置详情（按 id）；仓库未授权时按无权限回答，不用「不存在」。
     */
    public InventoryWarningThresholdVO detail(Long warningThresholdId) {
        InventoryWarningThresholdVO vo = inventoryWarningThresholdDao.detail(warningThresholdId);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_NOT_FOUND);
        }
        if (!dataScopeService.resolve().getWarehouseScope().allows(vo.getWarehouseId())) {
            throw new ScmDataScopeException();
        }
        return vo;
    }

    /**
     * 预警列表。
     *
     * <p>
     * {@code status} 为空时 SQL 只返回异常项（LOW / HIGH）—— 这是<b>预警列表</b>的默认语义，一个全是正常项的列表对使用者没有意义。
     *
     * <p>
     * 每行的状态在这里按<b>可用量</b>重算（而不是信 SQL 的结果）：可用量是 SQL 算出来的数字，而「数字 vs 阈值 → 状态」这条规则只在枚举里实现一次。
     */
    public PageResult<InventoryWarningVO> queryWarningPage(InventoryWarningQueryForm query) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryWarningVO> list = inventoryWarningThresholdDao.queryWarningPage(page, query,
            scope.getWarehouseScope());
        list.forEach(InventoryWarningQueryService::fillStatus);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    private static void fillStatus(InventoryWarningVO vo) {
        ScmInventoryWarningStatusEnum status = ScmInventoryWarningStatusEnum.evaluate(vo.getAvailableQuantity(),
            vo.getWarnMin(), vo.getWarnMax());
        vo.setStatus(status.name());
        vo.setStatusDesc(status.getDesc());
    }
}

package net.lab1024.sa.admin.module.scm.inventory.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryWarningThresholdDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningThresholdVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_NOT_FOUND;

/**
 * 库存预警与阈值配置的查询侧（只读）。
 *
 * <p><b>预警状态在服务层按可用量算出，不落库</b>：它完全由 {@code (阈值, 可用量)} 决定，
 * 落库只会多出一个会漂移的副本，而不会多出任何信息（参考项目写「余额变动后校验」，
 * 那意味着**六条**余额写入路径都要顺手维护这个状态）。
 *
 * <p><b>状态判定的实现只有一处</b>（{@code ScmInventoryWarningStatusEnum#evaluate}）。
 * 列表 SQL 里另有一份**过滤**用的谓词（为了能按状态筛选 + 分页），
 * 两者的等价性由 {@code ScmInventoryWarningIT} 交叉验证：用 {@code status=LOW} 查出来的行，
 * 其服务层算出的状态必须全是 {@code LOW}。
 */
@Service
@RequiredArgsConstructor
public class InventoryWarningQueryService {

    private final InventoryWarningThresholdDao thresholdDao;

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
        List<InventoryWarningThresholdVO> list =
                thresholdDao.queryPage(page, query, scope.getWarehouseScope());
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 阈值配置详情（按 id）；仓库未授权时按无权限回答，不用「不存在」。
     */
    public InventoryWarningThresholdVO detail(Long id) {
        InventoryWarningThresholdVO vo = thresholdDao.detail(id);
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
     * <p>{@code status} 为空时 SQL 只返回异常项（LOW / HIGH）—— 这是**预警列表**的默认语义，
     * 一个全是正常项的列表对使用者没有意义。
     *
     * <p>每行的状态在这里按**可用量**重算（而不是信 SQL 的结果）：可用量是 SQL 算出来的数字，
     * 而「数字 vs 阈值 → 状态」这条规则只在枚举里实现一次。
     */
    public PageResult<InventoryWarningVO> queryWarningPage(InventoryWarningQueryForm query) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryWarningVO> list = thresholdDao.queryWarningPage(page, query, scope.getWarehouseScope());
        list.forEach(InventoryWarningQueryService::fillStatus);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    private static void fillStatus(InventoryWarningVO vo) {
        ScmInventoryWarningStatusEnum status = ScmInventoryWarningStatusEnum.evaluate(
                vo.getAvailableQuantity(), vo.getWarnMin(), vo.getWarnMax());
        vo.setStatus(status.name());
        vo.setStatusDesc(status.getDesc());
    }
}

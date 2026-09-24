package net.lab1024.sa.admin.module.scm.inventory.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryInTransitVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferItemVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_NOT_FOUND;

/**
 * 调拨单查询侧（只读）。
 *
 * <p>与命令侧分离：查询不参与事务、不加锁，且**不做权限以外的业务判断**。
 * 状态中文描述在服务层按枚举填充，避免前端硬编码状态字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferQueryService {

    private final InventoryTransferDao transferDao;

    private final InventoryTransferItemDao itemDao;

    private final ScmDataScopeService dataScopeService;

    /**
     * 在途库存报表（只读聚合，不进 inventory_balance）。
     */
    public List<InventoryInTransitVO> queryInTransit() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return List.of();
        }
        return transferDao.queryInTransit(scope.getWarehouseScope());
    }

    /**
     * 分页查询（不返回明细，明细走 {@link #detail}）。
     */
    public PageResult<InventoryTransferVO> queryPage(InventoryTransferQueryForm query) {
        // 排序由 mapper 写死（created_at DESC, id DESC），这里不注入 OrderItem ——
        // 列表是双联表结果（warehouse 联了两次），客户端传入的排序列名会与 join 列产生歧义。
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryTransferVO> list = transferDao.queryPage(page, query, scope.getWarehouseScope());
        list.forEach(InventoryTransferQueryService::fillStatusDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 详情（含明细，按录入顺序）。调拨两端任一到授权仓即可见，两端都不授权时按无权限回答。
     */
    public InventoryTransferVO detail(Long id) {
        InventoryTransferVO vo = transferDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_NOT_FOUND);
        }
        ScmValueScope warehouseScope = dataScopeService.resolve().getWarehouseScope();
        if (!warehouseScope.allows(vo.getFromWarehouseId()) && !warehouseScope.allows(vo.getToWarehouseId())) {
            throw new ScmDataScopeException();
        }
        fillStatusDesc(vo);
        List<InventoryTransferItemVO> items = itemDao.listByTransferId(id);
        vo.setItems(items.stream().map(InventoryTransferQueryService::toItem).toList());
        return vo;
    }

    private static void fillStatusDesc(InventoryTransferVO vo) {
        for (ScmInventoryTransferStatusEnum item : ScmInventoryTransferStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }

    private static InventoryTransferVO.Item toItem(InventoryTransferItemVO src) {
        InventoryTransferVO.Item item = new InventoryTransferVO.Item();
        item.setId(src.getId());
        item.setSkuId(src.getSkuId());
        item.setSkuCode(src.getSkuCode());
        item.setSkuName(src.getSkuName());
        item.setProductName(src.getProductName());
        item.setSpecValues(src.getSpecValues());
        item.setQuantity(src.getQuantity());
        item.setUnitSnapshot(src.getUnitSnapshot());
        item.setRemark(src.getRemark());
        return item;
    }
}

package com.xsy.scm.inventory.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.constant.ScmInventoryOutboundStatusEnum;
import com.xsy.scm.inventory.dao.InventoryOutboundDao;
import com.xsy.scm.inventory.dao.InventoryOutboundItemDao;
import com.xsy.scm.inventory.domain.form.InventoryOutboundQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryOutboundItemVO;
import com.xsy.scm.inventory.domain.vo.InventoryOutboundVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_NOT_FOUND;

/**
 * 出库单查询侧（只读）。
 *
 * <p>
 * 与命令侧分离：查询不参与事务、不加锁，且**不做权限以外的业务判断**。 状态中文描述在服务层按枚举填充，避免前端硬编码状态字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryOutboundQueryService {

    private final InventoryOutboundDao inventoryOutboundDao;

    private final InventoryOutboundItemDao inventoryOutboundItemDao;

    private final ScmDataScopeService dataScopeService;

    /**
     * 分页查询（不返回明细，明细走 {@link #detail}）。
     */
    public PageResult<
            InventoryOutboundVO> queryPage(InventoryOutboundQueryForm query) {
        // 排序由 mapper 写死（created_at DESC, id DESC），这里不注入 OrderItem ——
        // 列表是联表结果，客户端传入的排序列名会与 join 列产生歧义。
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<
                InventoryOutboundVO> list = inventoryOutboundDao.queryPage(page, query, scope.getWarehouseScope());
        list.forEach(InventoryOutboundQueryService::fillStatusDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 详情（含明细，按录入顺序）；仓库未授权时按无权限回答，不用「不存在」。
     */
    public InventoryOutboundVO detail(Long outboundId) {
        InventoryOutboundVO vo = inventoryOutboundDao.detail(outboundId);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_NOT_FOUND);
        }
        if (!dataScopeService.resolve().getWarehouseScope().allows(vo.getWarehouseId())) {
            throw new ScmDataScopeException();
        }
        fillStatusDesc(vo);
        List<
                InventoryOutboundItemVO> items = inventoryOutboundItemDao.listByOutboundId(outboundId);
        vo.setItems(items.stream().map(InventoryOutboundQueryService::toItem).toList());
        return vo;
    }

    private static void fillStatusDesc(InventoryOutboundVO vo) {
        for (ScmInventoryOutboundStatusEnum item : ScmInventoryOutboundStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }

    private static InventoryOutboundVO.Item toItem(InventoryOutboundItemVO src) {
        InventoryOutboundVO.Item item = new InventoryOutboundVO.Item();
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

package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryStocktakeStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryStocktakeDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryStocktakeItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeItemVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_NOT_FOUND;

/**
 * 盘点单查询侧（只读）。
 *
 * <p>与命令侧分离：查询不参与事务、不加锁，且**不做权限以外的业务判断**。
 * 状态中文描述在服务层按枚举填充，避免前端硬编码状态字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeQueryService {

    private final InventoryStocktakeDao stocktakeDao;

    private final InventoryStocktakeItemDao itemDao;

    /**
     * 分页查询（不返回明细，明细走 {@link #detail}）。
     */
    public PageResult<InventoryStocktakeVO> queryPage(InventoryStocktakeQueryForm query) {
        // 排序由 mapper 写死（created_at DESC, id DESC），这里不注入 OrderItem ——
        // 列表是联表结果，客户端传入的排序列名会与 join 列产生歧义。
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryStocktakeVO> list = stocktakeDao.queryPage(page, query);
        list.forEach(InventoryStocktakeQueryService::fillStatusDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 详情（含明细，按录入顺序）。
     */
    public InventoryStocktakeVO detail(Long id) {
        InventoryStocktakeVO vo = stocktakeDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_NOT_FOUND);
        }
        fillStatusDesc(vo);
        List<InventoryStocktakeItemVO> items = itemDao.listByStocktakeId(id);
        vo.setItems(items.stream().map(InventoryStocktakeQueryService::toItem).toList());
        return vo;
    }

    private static void fillStatusDesc(InventoryStocktakeVO vo) {
        for (ScmInventoryStocktakeStatusEnum item : ScmInventoryStocktakeStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }

    private static InventoryStocktakeVO.Item toItem(InventoryStocktakeItemVO src) {
        InventoryStocktakeVO.Item item = new InventoryStocktakeVO.Item();
        item.setId(src.getId());
        item.setSkuId(src.getSkuId());
        item.setSkuCode(src.getSkuCode());
        item.setSkuName(src.getSkuName());
        item.setProductName(src.getProductName());
        item.setSpecValues(src.getSpecValues());
        item.setBookQuantity(src.getBookQuantity());
        item.setActualQuantity(src.getActualQuantity());
        item.setDeltaQuantity(src.getDeltaQuantity());
        item.setUnitSnapshot(src.getUnitSnapshot());
        item.setRemark(src.getRemark());
        return item;
    }
}

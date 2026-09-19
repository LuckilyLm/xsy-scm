package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryLossGainDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryLossGainItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainItemVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_NOT_FOUND;

/**
 * 报损报溢单查询侧（只读）。
 *
 * <p>与命令侧分离：查询不参与事务、不加锁，且**不做权限以外的业务判断**。
 * 调整类型与状态的中文描述在服务层按枚举填充，避免前端硬编码字典
 * —— 这两个字典一旦分叉，审批人看到的状态就会与后端不一致。
 */
@Service
@RequiredArgsConstructor
public class InventoryLossGainQueryService {

    private final InventoryLossGainDao lossGainDao;

    private final InventoryLossGainItemDao itemDao;

    /** 分页查询（不返回明细，明细走 {@link #detail}）。 */
    public PageResult<InventoryLossGainVO> queryPage(InventoryLossGainQueryForm query) {
        // 排序由 mapper 写死（created_at DESC, id DESC），这里不注入 OrderItem ——
        // 列表是联表结果，客户端传入的排序列名会与 join 列产生歧义。
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryLossGainVO> list = lossGainDao.queryPage(page, query);
        list.forEach(InventoryLossGainQueryService::fillDescs);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /** 详情（含明细，按录入顺序）。 */
    public InventoryLossGainVO detail(Long id) {
        InventoryLossGainVO vo = lossGainDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_NOT_FOUND);
        }
        fillDescs(vo);
        List<InventoryLossGainItemVO> items = itemDao.listByLossGainId(id);
        vo.setItems(items.stream().map(InventoryLossGainQueryService::toItem).toList());
        return vo;
    }

    private static void fillDescs(InventoryLossGainVO vo) {
        for (ScmInventoryLossGainTypeEnum item : ScmInventoryLossGainTypeEnum.values()) {
            if (item.name().equals(vo.getAdjustType())) {
                vo.setAdjustTypeDesc(item.getDesc());
                break;
            }
        }
        for (ScmInventoryLossGainStatusEnum item : ScmInventoryLossGainStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                break;
            }
        }
    }

    private static InventoryLossGainVO.Item toItem(InventoryLossGainItemVO src) {
        InventoryLossGainVO.Item item = new InventoryLossGainVO.Item();
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

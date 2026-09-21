package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryConversionDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryConversionItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionItemVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_NOT_FOUND;

/**
 * 规格转换单查询侧（只读）。
 *
 * <p>与命令侧分离：查询不参与事务、不加锁。类型与状态的中文描述在服务层按枚举填充，
 * 避免前端硬编码字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryConversionQueryService {

    private final InventoryConversionDao conversionDao;

    private final InventoryConversionItemDao itemDao;

    /**
     * 分页查询（不返回明细）。
     */
    public PageResult<InventoryConversionVO> queryPage(InventoryConversionQueryForm query) {
        // 排序由 mapper 写死（created_at DESC, id DESC），不注入 OrderItem ——
        // 列表是联表结果，客户端传入的排序列名会与 join 列产生歧义。
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryConversionVO> list = conversionDao.queryPage(page, query);
        list.forEach(InventoryConversionQueryService::fillDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 详情（含明细，按录入顺序）。
     */
    public InventoryConversionVO detail(Long id) {
        InventoryConversionVO vo = conversionDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_NOT_FOUND);
        }
        fillDesc(vo);
        List<InventoryConversionItemVO> items = itemDao.listByConversionId(id);
        vo.setItems(items.stream().map(InventoryConversionQueryService::toItem).toList());
        return vo;
    }

    /**
     * 独立明细 VO → 头内嵌明细。
     *
     * <p>两个类字段一致但**不复用同一个类**：头内嵌明细是「详情的组成部分」，
     * 独立投影是「一行的视图」，两者演进理由不同 —— 因此需要这一层显式映射
     * （与出库 / 盘点 / 报损报溢 / 调拨同一取向）。
     */
    private static InventoryConversionVO.Item toItem(InventoryConversionItemVO src) {
        InventoryConversionVO.Item item = new InventoryConversionVO.Item();
        item.setId(src.getId());
        item.setSourceSkuId(src.getSourceSkuId());
        item.setSourceSkuCode(src.getSourceSkuCode());
        item.setSourceSkuName(src.getSourceSkuName());
        item.setSourceProductName(src.getSourceProductName());
        item.setSourceQuantity(src.getSourceQuantity());
        item.setSourceUnit(src.getSourceUnit());
        item.setTargetSkuId(src.getTargetSkuId());
        item.setTargetSkuCode(src.getTargetSkuCode());
        item.setTargetSkuName(src.getTargetSkuName());
        item.setTargetProductName(src.getTargetProductName());
        item.setTargetQuantity(src.getTargetQuantity());
        item.setTargetUnit(src.getTargetUnit());
        item.setRemark(src.getRemark());
        return item;
    }

    private static void fillDesc(InventoryConversionVO vo) {
        ScmInventoryConversionTypeEnum type =
                ScmInventoryConversionTypeEnum.of(vo.getConvertType());
        if (type != null) {
            vo.setConvertTypeDesc(type.getDesc());
        }
        for (ScmInventoryConversionStatusEnum item : ScmInventoryConversionStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }
}

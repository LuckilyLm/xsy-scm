package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryWarningThresholdDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryWarningThresholdEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuDao;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_SKU_NOT_FOUND;

/**
 * 预警阈值配置的命令侧：新建 / 编辑 / 删除。
 *
 * <p><b>本类不碰 {@code inventory_balance}</b>：阈值是配置，余额是派生状态。
 * 若阈值放在余额表上，这个类就必须为了写阈值而创建余额行 ——
 * 那会造出「没有任何流水支撑的余额行」，直接破坏「余额是流水的净和」这条不变量。
 * 单独建表让配置路径完全不必触碰余额表。
 *
 * <p><b>并发口径</b>：编辑用实体自带的 {@code @Version} 乐观锁。
 * 版本号取自本事务刚读到的行，因此它主要起「版本号持续推进」的作用，
 * 而不是挡住「两个人在不同时间先后保存」—— 配置表一行只对应一个 (仓库, SKU)，
 * 争用极低，与其它库存单据一致采用最后写入者生效。
 */
@Service
@RequiredArgsConstructor
public class InventoryWarningThresholdService {

    private final InventoryWarningThresholdDao thresholdDao;

    private final WarehouseService warehouseService;

    private final ProductSkuDao productSkuDao;

    /**
     * 新建阈值配置。
     *
     * <p>同一个 (仓库, SKU) 只允许一条有效配置：两条会让「按哪条判断」变得没有答案，
     * 而预警是给人看的，含糊的预警等于没有预警。DB 有部分唯一索引兜底，这里先给出可读错误。
     *
     * @return 新配置 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryWarningThresholdAddForm form) {
        requireRange(form);
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());
        requireSkuExists(form.getSkuId());

        if (thresholdDao.countByWarehouseAndSku(form.getWarehouseId(), form.getSkuId()) > 0) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_DUPLICATE);
        }

        InventoryWarningThresholdEntity entity = new InventoryWarningThresholdEntity();
        entity.setWarehouseId(form.getWarehouseId());
        entity.setSkuId(form.getSkuId());
        entity.setWarnMin(form.getWarnMin());
        entity.setWarnMax(form.getWarnMax());
        entity.setRemark(form.getRemark());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        thresholdDao.insert(entity);
        return entity.getId();
    }

    /** 编辑阈值配置。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryWarningThresholdAddForm form) {
        requireRange(form);
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());
        requireSkuExists(form.getSkuId());

        InventoryWarningThresholdEntity existing = thresholdDao.selectById(id);
        if (existing == null) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_NOT_FOUND);
        }

        // 改了 (仓库, SKU) 就要重新防重：否则会把两条配置合成一条，撞唯一索引时报出的
        // 是 DB 错误而不是可读的业务错误。
        if (!Objects.equals(existing.getWarehouseId(), form.getWarehouseId())
                || !Objects.equals(existing.getSkuId(), form.getSkuId())) {
            if (thresholdDao.countByWarehouseAndSku(form.getWarehouseId(), form.getSkuId()) > 0) {
                throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_DUPLICATE);
            }
        }

        // 手写 SQL 而不是 updateById：实体的 updateStrategy = ALWAYS 会把 created_at 等
        // 也写进 SET 子句（更新实体里它们是 null → 违反 NOT NULL）。
        // 手写 SQL 同时天然支持把 warn_min / warn_max 清空成 NULL（取消该方向的预警）。
        if (thresholdDao.updateThreshold(id, form.getWarehouseId(), form.getSkuId(),
                form.getWarnMin(), form.getWarnMax(), form.getRemark(),
                existing.getVersion(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 删除阈值配置（逻辑删）。删除后该 (仓库, SKU) 不再产生预警。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InventoryWarningThresholdEntity existing = thresholdDao.selectById(id);
        if (existing == null) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_NOT_FOUND);
        }
        if (thresholdDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 阈值区间校验：至少一个边界、都非负、下限不高于上限。
     *
     * <p>三条判据合成一个错误码，因为对用户的补救动作是同一个「改一下配置」；
     * 具体是哪一种由前端表单先提示，后端只做兜底（DB 的 CHECK 再兜一层）。
     */
    private static void requireRange(InventoryWarningThresholdAddForm form) {
        if (form == null || (form.getWarnMin() == null && form.getWarnMax() == null)) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_INVALID);
        }
        if (form.getWarnMin() != null && form.getWarnMin().signum() < 0) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_INVALID);
        }
        if (form.getWarnMax() != null && form.getWarnMax().signum() < 0) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_INVALID);
        }
        if (form.getWarnMin() != null && form.getWarnMax() != null
                && form.getWarnMin().compareTo(form.getWarnMax()) > 0) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_INVALID);
        }
    }

    /**
     * SKU 必须存在。
     *
     * <p>其它库存单据不校验 SKU 存在（它们总由已存在的 SKU 选择器驱动），
     * 但**配置表是长期驻留的**：一条指向不存在 SKU 的配置会永远留在预警列表里
     * （没有余额 → 数量按 0 计 → 触发下限预警），成为永远清不掉的噪声。
     */
    private void requireSkuExists(Long skuId) {
        if (skuId == null || productSkuDao.selectById(skuId) == null) {
            throw new ScmBusinessException(INVENTORY_WARNING_THRESHOLD_SKU_NOT_FOUND);
        }
    }
}

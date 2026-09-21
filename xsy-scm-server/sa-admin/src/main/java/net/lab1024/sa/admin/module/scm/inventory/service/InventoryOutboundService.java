package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryOutboundStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryOutboundAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryOutboundItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_EMPTY_ITEMS;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_STATUS_INVALID;

/**
 * 出库单命令侧：创建 / 改草稿 / 确认出库 / 取消 / 删除。
 *
 * <p><b>状态机</b>：{@code DRAFT → CONFIRMED}，草稿可 {@code → CANCELLED}。
 * 已确认不可回退 —— 库存流水 append-only，冲销必须新增反向流水，不能改回草稿再删流水
 * （后者会被 {@code ck_inventory_movement_append_only} 在 DB 层拒绝）。
 *
 * <p><b>锁序（与收货确认同一顺序）</b>：
 * <ol>
 *   <li>先锁出库单头（{@code lockById}）；</li>
 *   <li>再按 {@code (warehouseId, skuId)} **升序**逐行锁余额并写流水。</li>
 * </ol>
 * 顺序固定是避免两条链路（收货确认 / 出库确认）以相反顺序拿余额锁而死锁。
 *
 * <p><b>全部明细在同一事务内</b>：任一行可用量不足或单位不一致，整单回滚 ——
 * 不允许「出一半」的部分出库。
 */
@Service
@RequiredArgsConstructor
public class InventoryOutboundService {

    private final InventoryOutboundDao outboundDao;

    private final InventoryOutboundItemDao itemDao;

    private final InventoryOutboundNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    /**
     * 新建草稿出库单。
     *
     * <p>草稿阶段**不校验可用量与单位** —— 那是确认出库时的判断。
     * 草稿允许「先录单再补货」，提前卡住反而让录单不可用。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryOutboundAddForm form) {
        requireItems(form);
        String operator = ScmOperator.current();

        InventoryOutboundEntity entity = new InventoryOutboundEntity();
        entity.setOutboundNo(numberGenerator.next());
        entity.setWarehouseId(form.getWarehouseId());
        entity.setStatus(ScmInventoryOutboundStatusEnum.DRAFT.name());
        entity.setRemark(form.getRemark());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        outboundDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /**
     * 改草稿：只允许 DRAFT；明细整表替换（逻辑删旧 + 插新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryOutboundAddForm form) {
        requireItems(form);
        String operator = ScmOperator.current();

        InventoryOutboundEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryOutboundStatusEnum.DRAFT);

        if (outboundDao.updateDraft(id, form.getWarehouseId(), form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        itemDao.deleteByOutboundId(id, operator);
        insertItems(id, form, operator);
    }

    /**
     * 确认出库：写 SALES_OUT 流水并扣减余额，全部在同一事务内。
     *
     * <p>先锁单据再锁余额；余额按 {@code (warehouseId, skuId)} 升序处理。
     * 明细行的 {@code unitSnapshot} 在此刻按余额记账单位回写 —— 草稿态它为空。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryOutboundEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryOutboundStatusEnum.DRAFT);

        List<InventoryOutboundItemVO> items = itemDao.listByOutboundId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_EMPTY_ITEMS);
        }

        // 锁序：余额锁按 (warehouseId, skuId) 升序 —— 同一单内多行也必须固定顺序。
        items.stream()
                .sorted(Comparator.comparing(InventoryOutboundItemVO::getSkuId))
                .forEach(item -> {
                    InventoryOutboundFact fact = new InventoryOutboundFact(
                            locked.getWarehouseId(),
                            item.getSkuId(),
                            locked.getId(),
                            item.getId(),
                            item.getQuantity(),
                            null,
                            now,
                            operator);
                    // 单位以余额记账单位为准（Q13），由命令服务返回，这里回写到明细行
                    String unit = inventoryCommandService.postSalesOutbound(fact);
                    itemDao.updateUnitSnapshot(item.getId(), unit, operator);
                });

        if (outboundDao.markConfirmed(id, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 取消草稿：不产生任何库存影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        String operator = ScmOperator.current();
        InventoryOutboundEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryOutboundStatusEnum.DRAFT);
        if (outboundDao.markCancelled(id, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除草稿（逻辑删）。已确认的单不可删 —— 它已产生流水，必须留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String operator = ScmOperator.current();
        InventoryOutboundEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryOutboundStatusEnum.DRAFT);
        itemDao.deleteByOutboundId(id, operator);
        if (outboundDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void insertItems(Long outboundId, InventoryOutboundAddForm form, String operator) {
        for (InventoryOutboundAddForm.Item item : form.getItems()) {
            InventoryOutboundItemEntity row = new InventoryOutboundItemEntity();
            row.setOutboundId(outboundId);
            row.setSkuId(item.getSkuId());
            row.setQuantity(item.getQuantity());
            row.setRemark(item.getRemark());
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            itemDao.insert(row);
        }
    }

    private static void requireItems(InventoryOutboundAddForm form) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_EMPTY_ITEMS);
        }
    }

    private InventoryOutboundEntity lockAndRequire(Long id) {
        InventoryOutboundEntity locked = outboundDao.lockById(id);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryOutboundEntity entity, ScmInventoryOutboundStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_STATUS_INVALID);
        }
    }
}

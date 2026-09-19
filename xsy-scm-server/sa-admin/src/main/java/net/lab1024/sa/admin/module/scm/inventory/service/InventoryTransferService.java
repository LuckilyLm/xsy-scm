package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryTransferFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryTransferEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryTransferItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferItemVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_DUPLICATE_SKU;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_EMPTY_ITEMS;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_SAME_WAREHOUSE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_STATUS_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_WAREHOUSE_DISABLED;

/**
 * 调拨单命令侧：创建 / 改草稿 / 发出 / 收货 / 取消 / 删除。
 *
 * <p><b>状态机</b>：{@code DRAFT → SHIPPED → RECEIVED}，草稿可 {@code → CANCELLED}。
 * 在途不可取消（货已物理离开源仓，只能靠反向调拨单冲回），两个终态都不可回退。
 *
 * <p><b>两步各自独立事务</b>（这是两步式的关键收益）：
 * <ul>
 *   <li>发出只锁**源仓**的余额行；</li>
 *   <li>收货只锁**目标仓**的余额行。</li>
 * </ul>
 * 因此既有的「按 {@code (warehouse_id, sku_id)} 升序锁余额」纪律**完全不用改**
 * —— 每个事务里只有一个仓库的行。若做成一步式，同一事务要锁两个仓库的行，
 * 锁序规则就得升级为跨仓排序，而那是六条既有写入路径都要跟着改的事。
 *
 * <p><b>锁序</b>：先锁单据头（{@code lockById}），再按 {@code (warehouseId, skuId)} 升序锁余额
 * —— 与收货 / 出库 / 盘点 / 报损报溢同一顺序。
 *
 * <p><b>全部明细在同一事务内</b>：任一行失败整单回滚，不允许「发一半」。
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferDao transferDao;

    private final InventoryTransferItemDao itemDao;

    private final InventoryTransferNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    /**
     * 新建草稿调拨单。
     *
     * <p>草稿阶段**不校验源仓是否有货**（那是发出时的判断）：草稿允许「先开单再备货」。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryTransferAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();

        InventoryTransferEntity entity = new InventoryTransferEntity();
        entity.setTransferNo(numberGenerator.next());
        entity.setFromWarehouseId(form.getFromWarehouseId());
        entity.setToWarehouseId(form.getToWarehouseId());
        entity.setStatus(ScmInventoryTransferStatusEnum.DRAFT.name());
        entity.setRemark(form.getRemark());
        // 草稿态不得有发出 / 收货信息（DB CHECK 也要求两者按状态为空）
        entity.setShippedAt(null);
        entity.setShippedBy(null);
        entity.setReceivedAt(null);
        entity.setReceivedBy(null);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        transferDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /** 改草稿：只允许 DRAFT；明细整表替换（逻辑删旧 + 插新）。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryTransferAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();

        InventoryTransferEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);

        if (transferDao.updateDraft(id, form.getFromWarehouseId(), form.getToWarehouseId(),
                form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        itemDao.deleteByTransferId(id, operator);
        insertItems(id, form, operator);
    }

    /**
     * 发出：从源仓扣减并写 {@code TRANSFER_OUT} 流水，单据进入**在途**。
     *
     * <p>明细行的 {@code unitSnapshot} 在此刻按源仓记账单位回写 —— 草稿态它为空。
     * 这个快照是收货时断言目标仓单位一致的依据。
     *
     * <p>源仓必须**启用**：停用仓库不能用于新的调拨业务（41048）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryTransferEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        requireEnabled(locked.getFromWarehouseId());

        List<InventoryTransferItemVO> items = itemDao.listByTransferId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_EMPTY_ITEMS);
        }

        // 锁序：余额锁按 (warehouseId, skuId) 升序 —— 同一单内多行也必须固定顺序。
        items.stream()
                .sorted(Comparator.comparing(InventoryTransferItemVO::getSkuId))
                .forEach(item -> {
                    InventoryTransferFact fact = new InventoryTransferFact(
                            locked.getFromWarehouseId(),
                            item.getSkuId(),
                            locked.getId(),
                            item.getId(),
                            item.getQuantity(),
                            // 发出不传单位：单位由源仓余额决定，命令服务会返回它
                            null,
                            now,
                            operator);
                    String unit = inventoryCommandService.postTransferOut(fact);
                    itemDao.updateUnitSnapshot(item.getId(), unit, operator);
                });

        if (transferDao.markShipped(id, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 收货：向目标仓累加并写 {@code TRANSFER_IN} 流水，单据完成。
     *
     * <p>目标仓必须**启用**（41048）。目标仓若从没有过该 SKU 的余额行，
     * 由本次调入建立（单位取明细快照）；已有则断言单位一致，不一致直接 41044。
     */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryTransferEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryTransferStatusEnum.SHIPPED);
        requireEnabled(locked.getToWarehouseId());

        List<InventoryTransferItemVO> items = itemDao.listByTransferId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_EMPTY_ITEMS);
        }

        items.stream()
                .sorted(Comparator.comparing(InventoryTransferItemVO::getSkuId))
                .forEach(item -> inventoryCommandService.postTransferIn(new InventoryTransferFact(
                        locked.getToWarehouseId(),
                        item.getSkuId(),
                        locked.getId(),
                        item.getId(),
                        item.getQuantity(),
                        // 收货必须传期望单位（来自发出时写入的快照）
                        item.getUnitSnapshot(),
                        now,
                        operator)));

        if (transferDao.markReceived(id, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 取消草稿：不产生任何库存影响。在途不可取消（货已出库，只能反向调拨冲回）。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        String operator = ScmOperator.current();
        InventoryTransferEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        if (transferDao.markCancelled(id, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 删除草稿（逻辑删）。已发出 / 已收货的单不可删 —— 它们已产生流水，必须留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String operator = ScmOperator.current();
        InventoryTransferEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        itemDao.deleteByTransferId(id, operator);
        if (transferDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void insertItems(Long transferId, InventoryTransferAddForm form, String operator) {
        for (InventoryTransferAddForm.Item item : form.getItems()) {
            InventoryTransferItemEntity row = new InventoryTransferItemEntity();
            row.setTransferId(transferId);
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

    /**
     * 单据级校验：至少一行、同一 SKU 不得重复、源仓与目标仓必须不同且都存在。
     *
     * <p>源仓 == 目标仓在这里判（而不是只靠 DB 的 {@code ck_inventory_transfer_distinct}）：
     * DB 约束给不出可读原因，而这是用户最容易犯的错。
     */
    private void requireForm(InventoryTransferAddForm form) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_EMPTY_ITEMS);
        }
        if (Objects.equals(form.getFromWarehouseId(), form.getToWarehouseId())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_SAME_WAREHOUSE);
        }
        Set<Long> seen = new HashSet<>();
        for (InventoryTransferAddForm.Item item : form.getItems()) {
            if (item == null || item.getSkuId() == null || !seen.add(item.getSkuId())) {
                throw new ScmBusinessException(INVENTORY_TRANSFER_DUPLICATE_SKU);
            }
        }
        // 两个仓库都必须存在（不存在时给出 40485，而不是让后续退化成「没有库存记录」）
        warehouseService.require(form.getFromWarehouseId());
        warehouseService.require(form.getToWarehouseId());
    }

    /**
     * 断言仓库**启用**。
     *
     * <p>与采购侧的 {@code PurchaseWarehouseReferenceGuard} 同一取向：
     * 「不允许用停用仓库建单」不是仓库域自身的不变量，因此错误码留在调用方域（41048）。
     */
    private void requireEnabled(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_WAREHOUSE_DISABLED);
        }
    }

    private InventoryTransferEntity lockAndRequire(Long id) {
        InventoryTransferEntity locked = transferDao.lockById(id);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryTransferEntity entity,
                                      ScmInventoryTransferStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_STATUS_INVALID);
        }
    }
}

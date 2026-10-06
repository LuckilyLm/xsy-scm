package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import com.xsy.scm.inventory.dao.InventoryTransferDao;
import com.xsy.scm.inventory.dao.InventoryTransferItemDao;
import com.xsy.scm.inventory.domain.InventoryTransferFact;
import com.xsy.scm.inventory.domain.entity.InventoryTransferEntity;
import com.xsy.scm.inventory.domain.entity.InventoryTransferItemEntity;
import com.xsy.scm.inventory.domain.form.InventoryTransferAddForm;
import com.xsy.scm.inventory.domain.vo.InventoryTransferItemVO;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_DUPLICATE_SKU;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_EMPTY_ITEMS;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_NOT_FOUND;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_SAME_WAREHOUSE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_STATUS_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_WAREHOUSE_DISABLED;

/**
 * 调拨单命令侧：创建 / 改草稿 / 发出 / 收货 / 取消 / 删除。
 *
 * <p>
 * 状态机 {@code DRAFT → SHIPPED → RECEIVED}，草稿可 {@code → CANCELLED}。在途不可取消（货已物理离开源仓， 只能靠反向调拨单冲回），两个终态都不可回退。
 *
 * <p>
 * 发出与收货各自独立事务：发出只锁源仓余额行，收货只锁目标仓余额行。因此既有的 「按 {@code (warehouse_id, sku_id)} 升序锁余额」纪律完全不用改 —— 每个事务里只有一个仓库的行。
 *
 * <p>
 * 锁序：先锁单据头（{@code lockById}），再按 {@code (warehouseId, skuId)} 升序锁余额，与收货 / 出库 / 盘点 / 报损报溢同一顺序。 全部明细在同一事务内，任一行失败整单回滚。
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferDao inventoryTransferDao;

    private final InventoryTransferItemDao inventoryTransferItemDao;

    private final InventoryTransferNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 新建草稿调拨单。
     *
     * <p>
     * 草稿阶段<b>不校验源仓是否有货</b>（那是发出时的判断）：草稿允许「先开单再备货」。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryTransferAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();
        // 两端都要授权：能建一张通往未授权仓的单，等于往自己读不到的仓库里塞一张待发出的单
        warehouseScopeGuard.requireAll(form.getFromWarehouseId(), form.getToWarehouseId());

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
        inventoryTransferDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /**
     * 改草稿：只允许 DRAFT；明细整表替换（逻辑删旧 + 插新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long transferId, InventoryTransferAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();

        InventoryTransferEntity locked = lockAndRequire(transferId);
        // 行上的两端 + 表单新选的两端：改单可以把任一端换仓，换进换出都必须在授权范围内
        warehouseScopeGuard.requireAll(locked.getFromWarehouseId(), locked.getToWarehouseId(),
                form.getFromWarehouseId(), form.getToWarehouseId());
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);

        if (inventoryTransferDao.updateDraft(transferId, form.getFromWarehouseId(), form.getToWarehouseId(),
                form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        inventoryTransferItemDao.deleteByTransferId(transferId, operator);
        insertItems(transferId, form, operator);
    }

    /**
     * 发出：从源仓扣减并写 {@code TRANSFER_OUT} 流水，单据进入<b>在途</b>。
     *
     * <p>
     * 明细行的 {@code unitSnapshot} 在此刻按源仓记账单位回写 —— 草稿态它为空。这个快照是收货时断言目标仓单位一致的依据。
     *
     * <p>
     * 源仓必须<b>启用</b>：停用仓库不能用于新的调拨业务（41048）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long transferId) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryTransferEntity locked = lockAndRequire(transferId);
        // 调拨三个动作的范围判据互不相同：查询任一端命中即可见、发出只看 from、收货只看 to
        warehouseScopeGuard.require(locked.getFromWarehouseId());
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        requireEnabled(locked.getFromWarehouseId());

        List<InventoryTransferItemVO> items = inventoryTransferItemDao.listByTransferId(transferId);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_EMPTY_ITEMS);
        }

        // 锁序：余额锁按 (warehouseId, skuId) 升序 —— 同一单内多行也必须固定顺序。
        items.stream().sorted(Comparator.comparing(InventoryTransferItemVO::getSkuId)).forEach(item -> {
            InventoryTransferFact fact = new InventoryTransferFact(locked.getFromWarehouseId(), item.getSkuId(),
                    locked.getId(), item.getId(), item.getQuantity(),
                    // 发出不传单位：单位由源仓余额决定，命令服务会返回它
                    null, now, operator);
            String unit = inventoryCommandService.postTransferOut(fact);
            inventoryTransferItemDao.updateUnitSnapshot(item.getId(), unit, operator);
        });

        if (inventoryTransferDao.markShipped(transferId, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 收货：向目标仓累加并写 {@code TRANSFER_IN} 流水，单据完成。
     *
     * <p>
     * 目标仓必须<b>启用</b>（41048）。目标仓若从没有过该 SKU 的余额行，由本次调入建立（单位取明细快照）；已有则断言单位一致，不一致直接 41044。
     */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long transferId) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryTransferEntity locked = lockAndRequire(transferId);
        // 收货只要求 to 端授权：货进的是目标仓的账，源仓的仓管不需要、也不应该能替它收货
        warehouseScopeGuard.require(locked.getToWarehouseId());
        requireStatus(locked, ScmInventoryTransferStatusEnum.SHIPPED);
        requireEnabled(locked.getToWarehouseId());

        List<InventoryTransferItemVO> items = inventoryTransferItemDao.listByTransferId(transferId);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_EMPTY_ITEMS);
        }

        items.stream().sorted(Comparator.comparing(InventoryTransferItemVO::getSkuId))
                .forEach(item -> inventoryCommandService.postTransferIn(new InventoryTransferFact(
                        locked.getToWarehouseId(), item.getSkuId(), locked.getId(), item.getId(), item.getQuantity(),
                        // 收货必须传期望单位（来自发出时写入的快照）
                        item.getUnitSnapshot(), now, operator)));

        if (inventoryTransferDao.markReceived(transferId, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 取消草稿：不产生任何库存影响。在途不可取消（货已出库，只能反向调拨冲回）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long transferId) {
        String operator = ScmOperator.current();
        InventoryTransferEntity locked = lockAndRequire(transferId);
        // 撤销草稿不碰任何余额，判据与查询同一条 OR：看得见这张单，就能撤掉它
        warehouseScopeGuard.requireAny(locked.getFromWarehouseId(), locked.getToWarehouseId());
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        if (inventoryTransferDao.markCancelled(transferId, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除草稿（逻辑删）。已发出 / 已收货的单不可删 —— 它们已产生流水，必须留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long transferId) {
        String operator = ScmOperator.current();
        InventoryTransferEntity locked = lockAndRequire(transferId);
        warehouseScopeGuard.requireAny(locked.getFromWarehouseId(), locked.getToWarehouseId());
        requireStatus(locked, ScmInventoryTransferStatusEnum.DRAFT);
        inventoryTransferItemDao.deleteByTransferId(transferId, operator);
        if (inventoryTransferDao.deleteById(transferId) != 1) {
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
            inventoryTransferItemDao.insert(row);
        }
    }

    /**
     * 单据级校验：至少一行、同一 SKU 不得重复、源仓与目标仓必须不同且都存在。
     *
     * <p>
     * 源仓 == 目标仓在这里判（而不是只靠 DB 的 {@code ck_inventory_transfer_distinct}）： DB 约束给不出可读原因，而这是用户最容易犯的错。
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
     * 断言仓库<b>启用</b>。
     *
     * <p>
     * 与采购侧的 {@code PurchaseWarehouseReferenceGuard} 同一取向： 「不允许用停用仓库建单」不是仓库域自身的不变量，因此错误码留在调用方域（41048）。
     */
    private void requireEnabled(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_WAREHOUSE_DISABLED);
        }
    }

    private InventoryTransferEntity lockAndRequire(Long transferId) {
        InventoryTransferEntity locked = inventoryTransferDao.lockById(transferId);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryTransferEntity entity, ScmInventoryTransferStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_STATUS_INVALID);
        }
    }
}

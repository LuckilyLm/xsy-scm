package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryStocktakeStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryStocktakeDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryStocktakeItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeAdjustment;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryStocktakeEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryStocktakeItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeItemVO;
import net.lab1024.sa.admin.module.scm.inventory.support.StocktakeSnapshotDriftException;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_DUPLICATE_SKU;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_EMPTY_ITEMS;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_STATUS_INVALID;

/**
 * 盘点单命令侧：创建 / 改草稿 / 确认盘点 / 取消 / 删除。
 *
 * <p><b>状态机</b>：{@code DRAFT → CONFIRMED}，草稿可 {@code → CANCELLED}。
 * 已确认不可回退 —— 库存流水 append-only，冲销必须新增反向流水，不能改回草稿再删流水
 * （后者会被 {@code trg_inventory_movement_append_only} 在 DB 层拒绝）。
 *
 * <p><b>锁序（与收货确认、出库确认同一顺序）</b>：
 * <ol>
 *   <li>先锁盘点单头（{@code lockById}）；</li>
 *   <li>再按 {@code (warehouseId, skuId)} **升序**逐行锁余额并写流水。</li>
 * </ol>
 * 顺序固定是避免三条链路（收货 / 出库 / 盘点）以相反顺序拿余额锁而死锁。
 *
 * <p><b>全部明细在同一事务内</b>：任一行调整失败（负库存 / 低于预留 / 无余额行）整单回滚 ——
 * 不允许「盘一半」。已写下的流水也随事务回滚（触发器拦的是 UPDATE/DELETE，不拦 INSERT 的回滚）。
 *
 * <p><b>账面量快照在保存草稿时读入</b>（{@link #insertItems}），不在建单时读一次就冻结：
 * 改草稿会重新快照，这样仓管发现「账面量变了」时只要重新保存即可刷新基线，
 * 不需要删单重建。
 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeService {

    private final InventoryStocktakeDao stocktakeDao;

    private final InventoryStocktakeItemDao itemDao;

    private final InventoryStocktakeNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final InventoryBalanceDao balanceDao;

    private final WarehouseService warehouseService;

    /**
     * 新建草稿盘点单。
     *
     * <p>草稿阶段**不校验差异能否落地**（会不会负库存 / 会不会低于预留量）——
     * 那是确认盘点时的判断。草稿允许「先录实盘数再调整预留」，提前卡住反而让录单不可用。
     *
     * <p>但**账面量必须读得到**：读不到余额行说明该 (仓库, SKU) 从未入库，
     * 既没有可对比的账面量，也无法确定记账单位（Q13），此时直接失败（41023）。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryStocktakeAddForm form) {
        requireItems(form);
        String operator = ScmOperator.current();
        // 仓库不存在时给出准确错误，而不是让下面退化成「没有库存记录」。
        warehouseService.require(form.getWarehouseId());

        InventoryStocktakeEntity entity = new InventoryStocktakeEntity();
        entity.setStocktakeNo(numberGenerator.next());
        entity.setWarehouseId(form.getWarehouseId());
        entity.setStatus(ScmInventoryStocktakeStatusEnum.DRAFT.name());
        entity.setRemark(form.getRemark());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        stocktakeDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /**
     * Excel 导入建草稿：在<b>同一事务内</b>先按 {@code (warehouseId, skuId)} 升序锁定并逐项核验每条来源余额，
     * 全部与导出快照一致后才复用 {@link #create} 落草稿。
     *
     * <p><b>为什么锁与创建必须在同一事务</b>：{@code create} 会在保存时重新快照账面量（非加锁读）。
     * 若「先校验、再另起事务保存」，两次之间任何入出库都会让保存的账面量偏离被核验的快照 ——
     * 正是要杜绝的「先校验再保存」竞态。这里持锁核验后直接在同一事务内 {@code create}，
     * 其重新快照读到的是本事务已锁定的行，必然等于核验值。
     *
     * <p><b>任一漂移即整批失败</b>：只要有一条来源余额的 id / 版本 / 单位 / 账面量与快照不符，
     * 就抛 {@link StocktakeSnapshotDriftException} 回滚，不产生任何草稿（计划裁决：版本变化不可忽略，
     * 即便数量变动后又恢复，版本也已在 {@link #confirm} 链路自增）。
     *
     * @return 新草稿单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSnapshot(Long warehouseId, List<SnapshotLine> lines) {
        List<SnapshotLine> ordered = lines.stream()
                .sorted(Comparator.comparing(SnapshotLine::skuId))
                .toList();
        for (SnapshotLine line : ordered) {
            InventoryBalanceEntity locked = balanceDao.lockByWarehouseAndSku(warehouseId, line.skuId());
            boolean drifted = locked == null
                    || !Objects.equals(locked.getId(), line.balanceId())
                    || !Objects.equals(locked.getVersion(), line.version())
                    || !Objects.equals(locked.getUnit(), line.unit())
                    || locked.getQuantity().compareTo(line.bookQuantity()) != 0;
            if (drifted) {
                throw new StocktakeSnapshotDriftException(line.skuCode());
            }
        }

        InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
        form.setWarehouseId(warehouseId);
        List<InventoryStocktakeAddForm.Item> items = new java.util.ArrayList<>();
        for (SnapshotLine line : lines) {
            InventoryStocktakeAddForm.Item item = new InventoryStocktakeAddForm.Item();
            item.setSkuId(line.skuId());
            item.setActualQuantity(line.actualQuantity());
            item.setRemark(line.remark());
            items.add(item);
        }
        form.setItems(items);
        return create(form);
    }

    /**
     * 导入建草稿的一行：既携带受保护快照（核验用），又携带用户填写的实盘量与备注。
     *
     * <p>{@code actualQuantity} / {@code remark} 来自用户在 Excel 里的编辑；
     * 其余四项来自签名凭证，导入方不得信任单元格。
     */
    public record SnapshotLine(String skuCode, Long skuId, Long balanceId, String unit,
                               Integer version, BigDecimal bookQuantity,
                               BigDecimal actualQuantity, String remark) {
    }

    /**
     * 改草稿：只允许 DRAFT；明细整表替换（逻辑删旧 + 插新），并**重新快照账面量**。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryStocktakeAddForm form) {
        requireItems(form);
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());

        InventoryStocktakeEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryStocktakeStatusEnum.DRAFT);

        if (stocktakeDao.updateDraft(id, form.getWarehouseId(), form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        itemDao.deleteByStocktakeId(id, operator);
        insertItems(id, form, operator);
    }

    /**
     * 确认盘点：把每行差异转成盘盈 / 盘亏流水并调整余额，全部在同一事务内。
     *
     * <p>先锁单据再锁余额；余额按 {@code (warehouseId, skuId)} 升序处理。
     * 明细行的 {@code unitSnapshot} 在此刻按余额记账单位回写 —— 草稿态它为空。
     *
     * <p>差异为 0 的行**不写流水**（数量恒为正），但仍会回写单位快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryStocktakeEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryStocktakeStatusEnum.DRAFT);

        List<InventoryStocktakeItemVO> items = itemDao.listByStocktakeId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_EMPTY_ITEMS);
        }

        // 锁序：余额锁按 (warehouseId, skuId) 升序 —— 同一单内多行也必须固定顺序。
        items.stream()
                .sorted(Comparator.comparing(InventoryStocktakeItemVO::getSkuId))
                .forEach(item -> {
                    InventoryStocktakeFact fact = new InventoryStocktakeFact(
                            locked.getWarehouseId(),
                            item.getSkuId(),
                            locked.getId(),
                            item.getId(),
                            item.getBookQuantity(),
                            item.getActualQuantity(),
                            now,
                            operator);
                    InventoryStocktakeAdjustment adjustment =
                            inventoryCommandService.postStocktakeAdjust(fact);
                    // 单位以余额记账单位为准（Q13），由命令服务返回，这里回写到明细行
                    itemDao.updateUnitSnapshot(item.getId(), adjustment.unit(), operator);
                });

        if (stocktakeDao.markConfirmed(id, now, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 取消草稿：不产生任何库存影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        String operator = ScmOperator.current();
        InventoryStocktakeEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryStocktakeStatusEnum.DRAFT);
        if (stocktakeDao.markCancelled(id, operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除草稿（逻辑删）。已确认的单不可删 —— 它已产生流水，必须留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String operator = ScmOperator.current();
        InventoryStocktakeEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryStocktakeStatusEnum.DRAFT);
        itemDao.deleteByStocktakeId(id, operator);
        if (stocktakeDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 插入明细，并为每行**重新快照账面量**。
     *
     * <p>快照取 {@code selectByWarehouseAndSku}（不加锁）：这是给仓管看的参考值与差异基线，
     * 不是账。真正决定调整结果的账面量在确认时持锁读取（见
     * {@code InventoryCommandService#postStocktakeAdjust}），因此这里不需要锁，
     * 也不需要与确认时的值一致。
     */
    private void insertItems(Long stocktakeId, InventoryStocktakeAddForm form, String operator) {
        for (InventoryStocktakeAddForm.Item item : form.getItems()) {
            InventoryBalanceEntity balance =
                    balanceDao.selectByWarehouseAndSku(form.getWarehouseId(), item.getSkuId());
            if (balance == null) {
                throw new ScmBusinessException(INVENTORY_STOCKTAKE_BALANCE_MISSING);
            }
            InventoryStocktakeItemEntity row = new InventoryStocktakeItemEntity();
            row.setStocktakeId(stocktakeId);
            row.setSkuId(item.getSkuId());
            row.setBookQuantity(balance.getQuantity());
            row.setActualQuantity(item.getActualQuantity());
            row.setRemark(item.getRemark());
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            itemDao.insert(row);
        }
    }

    /**
     * 明细校验：至少一行，且同一 SKU 不得重复。
     *
     * <p>重复 SKU 会让同一份差异被施加两次，而结果看起来完全正常（余额确实变了，
     * 只是变错了），因此必须在写库前挡掉，而不是静默去重。
     */
    private static void requireItems(InventoryStocktakeAddForm form) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_EMPTY_ITEMS);
        }
        Set<Long> seen = new HashSet<>();
        for (InventoryStocktakeAddForm.Item item : form.getItems()) {
            if (item == null || item.getSkuId() == null || !seen.add(item.getSkuId())) {
                throw new ScmBusinessException(INVENTORY_STOCKTAKE_DUPLICATE_SKU);
            }
        }
    }

    private InventoryStocktakeEntity lockAndRequire(Long id) {
        InventoryStocktakeEntity locked = stocktakeDao.lockById(id);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryStocktakeEntity entity,
                                      ScmInventoryStocktakeStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_STATUS_INVALID);
        }
    }
}

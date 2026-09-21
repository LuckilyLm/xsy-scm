package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryConversionDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryConversionItemDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryConversionFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryConversionEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryConversionItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionItemVO;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_EMPTY_ITEMS;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_REJECT_OPINION_REQUIRED;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_SAME_SKU;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_STATUS_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_WAREHOUSE_DISABLED;

/**
 * 规格转换单命令侧：创建 / 改待审核 / 审批 / 驳回 / 删除。
 *
 * <p><b>本波次的核心难点：一次转换要在同一事务里改动同一仓库的**两行**余额</b>
 * （源 SKU 与目标 SKU）。既有六条写入路径每个事务只碰一行，锁序天然成立；
 * 这里第一次碰两行，**必须显式排序**，否则「行 1 先锁 A 再锁 B、行 2 先锁 B 再锁 A」
 * 会死锁。
 *
 * <p>做法（见 {@link #approve}）：把每行拆成**两条腿**（转出腿 / 转入腿），
 * 全部收集后按 {@code (skuId, 方向)} 统一排序再逐条执行 ——
 * 同一仓库下按 skuId 升序即等价于既有的
 * 「按 {@code (warehouse_id, sku_id)} 升序锁余额」纪律。
 *
 * <p><b>同一 skuId 上先入后出</b>：若某 SKU 既是某行的目标、又是另一行的源
 * （链式转换，如 A→B 且 B→C），B 的「出」依赖 B 的「入」—— 先出后入会因为
 * B 还没入而失败，而用户的本意显然是链式。这是本单内**唯一**允许的次序依赖，已写明。
 *
 * <p><b>不做聚合</b>：每条腿对应一条流水，源身份是
 * {@code (CONVERT_OUT_ITEM|CONVERT_IN_ITEM, 明细行 id)} —— 聚合会让「一条明细行
 * 产生两条流水」的防重语义变得说不清（该用哪个行 id？）。
 */
@Service
@RequiredArgsConstructor
public class InventoryConversionService {

    private final InventoryConversionDao conversionDao;

    private final InventoryConversionItemDao itemDao;

    private final InventoryConversionNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    /**
     * 新建规格转换单（**创建即待审核**）。
     *
     * <p>不校验「能不能真的转换」：待审核阶段不影响库存，源 SKU 够不够货、
     * 单位是否一致都要等到审批时才知道（期间可能有出库）。提前卡住会让录单不可用。
     *
     * @return 新单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryConversionAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());

        InventoryConversionEntity entity = new InventoryConversionEntity();
        entity.setConversionNo(numberGenerator.next());
        entity.setWarehouseId(form.getWarehouseId());
        entity.setConvertType(form.getConvertType());
        entity.setStatus(ScmInventoryConversionStatusEnum.PENDING.name());
        entity.setReason(form.getReason());
        entity.setRemark(form.getRemark());
        // 待审核态不得有审核信息（DB 双侧 CHECK 也要求两者同时为空）
        entity.setAuditedAt(null);
        entity.setAuditor(null);
        entity.setAuditOpinion(null);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        conversionDao.insert(entity);

        insertItems(entity.getId(), form, operator);
        return entity.getId();
    }

    /**
     * 改待审核单据：只允许 PENDING；明细整表替换（逻辑删旧 + 插新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryConversionAddForm form) {
        requireForm(form);
        String operator = ScmOperator.current();
        warehouseService.require(form.getWarehouseId());

        InventoryConversionEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryConversionStatusEnum.PENDING);

        if (conversionDao.updatePending(id, form.getWarehouseId(), form.getConvertType(),
                form.getReason(), form.getRemark(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        itemDao.deleteByConversionId(id, operator);
        insertItems(id, form, operator);
    }

    /**
     * 审批通过：按明细写 {@code CONVERT_OUT} + {@code CONVERT_IN} 流水并调整两边余额。
     *
     * <p>锁序见类注释。全部明细在同一事务内：任一条腿失败整单回滚，
     * 不允许「转了一半」—— 那会让源 SKU 的货凭空消失。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, InventoryConversionAuditForm form) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        InventoryConversionEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryConversionStatusEnum.PENDING);
        requireVersion(locked, form);
        requireEnabled(locked.getWarehouseId());

        List<InventoryConversionItemVO> items = itemDao.listByConversionId(id);
        if (items == null || items.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_EMPTY_ITEMS);
        }

        // 成本基准必须在**任何腿写入之前**定好：下面的腿按 (skuId, 先入后出) 排序执行，
        // 同一条明细的转入腿完全可能先于转出腿跑。等到腿里再读均价，读到的会是被本单前半段
        // 改过的值，两条腿于是不同源 —— 守恒的是总成本，这一步就是它的唯一来源。
        // lockCostBasis 同时承担锁序职责：按 skuId 升序把本单涉及的行一次锁齐。
        List<Long> involvedSkuIds = new ArrayList<>(items.size() * 2);
        for (InventoryConversionItemVO item : items) {
            involvedSkuIds.add(item.getSourceSkuId());
            involvedSkuIds.add(item.getTargetSkuId());
        }
        Map<Long, BigDecimal> costBasis = resolveOutboundCostBasis(items,
                inventoryCommandService.lockCostBasis(locked.getWarehouseId(), involvedSkuIds));

        // 把每行拆成两条腿，再全局排序 —— 这是本能力与既有六条写入路径的**唯一实质差异**。
        List<Leg> legs = new ArrayList<>(items.size() * 2);
        for (InventoryConversionItemVO item : items) {
            // 一条明细的两条腿共用同一个基准值，所以总成本必然守恒。
            BigDecimal sourceCost = costBasis.getOrDefault(item.getSourceSkuId(), BigDecimal.ZERO);
            legs.add(new Leg(item.getSourceSkuId(), false, item.getId(),
                    item.getSourceQuantity(), item.getSourceUnit(), sourceCost));
            legs.add(new Leg(item.getTargetSkuId(), true, item.getId(),
                    item.getTargetQuantity(), item.getTargetUnit(),
                    InventoryCommandService.convertedUnitCost(
                            item.getSourceQuantity(), sourceCost, item.getTargetQuantity())));
        }
        // 按 skuId 升序（同仓，等价于 (warehouse_id, sku_id) 升序）；
        // 同一 skuId 时**先入后出**，让链式转换（A→B 且 B→C）能成立。
        legs.sort(Comparator.comparing(Leg::skuId)
                .thenComparing(leg -> leg.inbound() ? 0 : 1));

        for (Leg leg : legs) {
            InventoryConversionFact fact = new InventoryConversionFact(
                    locked.getWarehouseId(), locked.getId(), leg.itemId(),
                    leg.skuId(), leg.quantity(), leg.unit(), leg.unitCost(), now, operator);
            if (leg.inbound()) {
                inventoryCommandService.postConvertIn(fact);
            } else {
                inventoryCommandService.postConvertOut(fact);
            }
        }

        if (conversionDao.markCompleted(id, now, operator, form.getAuditOpinion(),
                form.getVersion()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 驳回：只允许 PENDING，**不产生任何库存影响**。审核意见必填（41063）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, InventoryConversionAuditForm form) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        if (form.getAuditOpinion() == null || form.getAuditOpinion().isBlank()) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_REJECT_OPINION_REQUIRED);
        }

        InventoryConversionEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryConversionStatusEnum.PENDING);
        requireVersion(locked, form);

        if (conversionDao.markRejected(id, now, operator, form.getAuditOpinion(),
                form.getVersion()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除待审核单据（逻辑删）。已审核的单不可删 —— 它们必须留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        String operator = ScmOperator.current();
        InventoryConversionEntity locked = lockAndRequire(id);
        requireStatus(locked, ScmInventoryConversionStatusEnum.PENDING);
        itemDao.deleteByConversionId(id, operator);
        if (conversionDao.deleteById(id) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 一条腿：一次对某行余额的增减。{@code unitCost} 是本单求解出的单位成本基准。
     */
    private record Leg(Long skuId, boolean inbound, Long itemId,
                       BigDecimal quantity, String unit, BigDecimal unitCost) {
    }

    /**
     * 求「某 SKU 作为转出腿时的单位成本基准」，输入是审批开始时持锁取到的期初快照。
     *
     * <p>不能直接拿期初均价当基准：腿的执行顺序保证同一 SKU **先入后出**，所以一个在本单里
     * 既收又发的 SKU（链式转换 A→B 且 B→C 里的 B），它的转出腿在真实账本上看到的均价是
     * 「进完之后」的加权值。B 没有期初行时期初均价为 0，直接取 0 会把 C 记成零成本 ——
     * 与本轮修掉的「调拨转入清零」是同一个缺陷。
     */
    private Map<Long, BigDecimal> resolveOutboundCostBasis(
            List<InventoryConversionItemVO> items,
            Map<Long, InventoryCommandService.CostBasis> opening) {
        Map<Long, List<InventoryConversionItemVO>> inboundByTarget = new HashMap<>();
        for (InventoryConversionItemVO item : items) {
            inboundByTarget.computeIfAbsent(item.getTargetSkuId(), key -> new ArrayList<>()).add(item);
        }
        Map<Long, BigDecimal> resolved = new HashMap<>();
        for (InventoryConversionItemVO item : items) {
            outboundCostBasis(item.getSourceSkuId(), inboundByTarget, opening, resolved, new HashSet<>());
        }
        return resolved;
    }

    /**
     * 沿单据的 SKU 引用图递归求基准：某 SKU 的基准 = 期初行与它在本单里收到的全部转入腿加权，
     * 而每条转入腿的成本又来自其源 SKU 的基准（同一条规则）。
     *
     * <p>循环引用（同一单里 A→B 且 B→A）没有定义良好的解，按期初均价收敛且不写缓存：
     * 每条明细的两条腿仍共用同一个基准值，总成本依旧守恒，只是转出腿的基准价可能与该 SKU
     * 当时的行均价不同。这类单据本身没有业务意义，不为它新增拒绝路径。
     *
     * <p>转入腿按 {@code items} 的顺序逐条加权，与腿的实际执行顺序一致（同一 SKU 上先入后出、
     * 入腿之间保持明细行顺序），因此中间取整也与账本一致。
     */
    private BigDecimal outboundCostBasis(
            Long skuId,
            Map<Long, List<InventoryConversionItemVO>> inboundByTarget,
            Map<Long, InventoryCommandService.CostBasis> opening,
            Map<Long, BigDecimal> resolved,
            Set<Long> visiting) {
        BigDecimal cached = resolved.get(skuId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(skuId)) {
            InventoryCommandService.CostBasis cyclic = opening.get(skuId);
            return cyclic == null ? BigDecimal.ZERO : cyclic.avgCostOrZero();
        }

        InventoryCommandService.CostBasis own = opening.get(skuId);
        BigDecimal quantity = own == null ? BigDecimal.ZERO : own.quantityOrZero();
        BigDecimal basis = own == null ? BigDecimal.ZERO : own.avgCostOrZero();

        List<InventoryConversionItemVO> inbound = inboundByTarget.get(skuId);
        if (inbound != null) {
            for (InventoryConversionItemVO item : inbound) {
                BigDecimal sourceCost = outboundCostBasis(
                        item.getSourceSkuId(), inboundByTarget, opening, resolved, visiting);
                basis = InventoryCommandService.weightedAvgCost(quantity, basis,
                        item.getTargetQuantity(),
                        InventoryCommandService.convertedUnitCost(
                                item.getSourceQuantity(), sourceCost, item.getTargetQuantity()));
                quantity = quantity.add(item.getTargetQuantity());
            }
        }

        visiting.remove(skuId);
        resolved.put(skuId, basis);
        return basis;
    }

    private void insertItems(Long conversionId, InventoryConversionAddForm form, String operator) {
        for (InventoryConversionAddForm.Item item : form.getItems()) {
            InventoryConversionItemEntity row = new InventoryConversionItemEntity();
            row.setConversionId(conversionId);
            row.setSourceSkuId(item.getSourceSkuId());
            row.setSourceQuantity(item.getSourceQuantity());
            row.setSourceUnit(item.getSourceUnit().trim());
            row.setTargetSkuId(item.getTargetSkuId());
            row.setTargetQuantity(item.getTargetQuantity());
            row.setTargetUnit(item.getTargetUnit().trim());
            row.setRemark(item.getRemark());
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            itemDao.insert(row);
        }
    }

    /**
     * 单据级校验：至少一行、每行源 SKU ≠ 目标 SKU、类型属于白名单、仓库存在。
     *
     * <p>**同一 SKU 允许出现在多行**（既是某行的源、又是另一行的目标），
     * 这是链式转换的合法形态，因此不做「SKU 不得重复」的校验
     * —— 与调拨 / 报损报溢相反（那里同一 SKU 重复一定是录单错误）。
     * 需要去重的不是 SKU 而是**余额行**，由审批时的全局排序保证。
     */
    private void requireForm(InventoryConversionAddForm form) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_EMPTY_ITEMS);
        }
        if (!ScmInventoryConversionTypeEnum.isSupported(form.getConvertType())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_PARAM_INVALID);
        }
        for (InventoryConversionAddForm.Item item : form.getItems()) {
            if (item == null || item.getSourceSkuId() == null || item.getTargetSkuId() == null
                    || item.getSourceQuantity() == null || item.getTargetQuantity() == null
                    || item.getSourceQuantity().signum() <= 0
                    || item.getTargetQuantity().signum() <= 0
                    || item.getSourceUnit() == null || item.getSourceUnit().isBlank()
                    || item.getTargetUnit() == null || item.getTargetUnit().isBlank()) {
                throw new ScmBusinessException(INVENTORY_CONVERSION_PARAM_INVALID);
            }
            if (Objects.equals(item.getSourceSkuId(), item.getTargetSkuId())) {
                throw new ScmBusinessException(INVENTORY_CONVERSION_SAME_SKU);
            }
        }
    }

    /**
     * 断言仓库**启用**（与调拨 / 采购同一取向：码留在调用方域）。
     */
    private void requireEnabled(Long warehouseId) {
        WarehouseEntity warehouse = warehouseService.require(warehouseId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_WAREHOUSE_DISABLED);
        }
    }

    /**
     * 乐观锁早失败：版本不符时**在写流水之前**就报错，不做无用功。
     */
    private static void requireVersion(InventoryConversionEntity entity,
                                       InventoryConversionAuditForm form) {
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private InventoryConversionEntity lockAndRequire(Long id) {
        InventoryConversionEntity locked = conversionDao.lockById(id);
        if (locked == null) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_NOT_FOUND);
        }
        return locked;
    }

    private static void requireStatus(InventoryConversionEntity entity,
                                      ScmInventoryConversionStatusEnum expected) {
        if (!expected.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_STATUS_INVALID);
        }
    }
}

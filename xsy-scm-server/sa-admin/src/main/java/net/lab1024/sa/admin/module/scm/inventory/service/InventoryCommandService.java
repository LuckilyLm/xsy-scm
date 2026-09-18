package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryMovementEntity;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH;

/**
 * 库存命令服务（W6 Target Design §6.2）——**库存域唯一的写路径**。
 *
 * <p>W6-1 的写入只有一个来源：已确认的采购收货行（{@link #postPurchaseInbound}）。
 * 没有手工调账 API、没有出库、没有盘点，因此「余额是怎么变成这个数的」永远可以回溯到流水。
 *
 * <p><b>事务</b>：本类**不声明** {@code @Transactional}，也不使用
 * {@code REQUIRES_NEW} —— 它必须**加入调用方（收货确认）的事务**。
 * 这是「收货确认与入库同事务」这条契约的实现形态：库存任一步失败 → 收货确认整体回滚
 * （含 {@code received_quantity} 累计、状态机、幂等记录），不存在「采购成功、库存失败」的中间态。
 *
 * <p><b>锁序（§8.1）</b>：余额行锁永远是整个事务里最后获取的锁。本类内部不获取任何
 * 采购/收货锁；多行收货的余额加锁顺序由调用方（{@code PurchaseReceiptService.confirm}）
 * 按 {@code (warehouseId, skuId)} 升序预先排好，因此并发 confirm 之间不会形成环。
 *
 * <p><b>事实来源纪律（Q13-附）</b>：{@code occurredAt} 与 {@code operator} **一律取自入参事实**
 * （即 {@code purchase_receipt.confirmed_at} / {@code operator}）。
 * 本类**禁止**使用 {@code OffsetDateTime.now()}，也**禁止**使用
 * {@code ScmOperator.current()} 作为流水的 operator —— 否则 backfill 回放的历史流水
 * 会与实时路径写出两套时间/操作者语义，审计链条断裂。
 */
@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private final InventoryBalanceDao balanceDao;

    private final InventoryMovementDao movementDao;

    private final WarehouseService warehouseService;

    // ------------------------------------------------------------------
    // 写路径：采购入库
    // ------------------------------------------------------------------

    /**
     * 采购入库（{@code PURCHASE_IN}）：一次「已确认收货行」的入库事实落地。
     *
     * <p>序列与设计 §6.2 逐条对应：
     * <ol>
     *   <li>校验事实（quantity &gt; 0、unit / occurredAt / operator 非空）→ 41003；</li>
     *   <li>仓库引用守卫（{@code WarehouseService.require} → 40485，服务层外键替代，无 DB 外键）；</li>
     *   <li>余额行 {@code INSERT ... ON CONFLICT ... DO NOTHING} 后 {@code SELECT ... FOR UPDATE}
     *       —— 并发首建的两个事务最终拿到**同一行**（§8.2）；</li>
     *   <li>单位不变量（Q13）：{@code fact.unit != balance.unit} → 41001，confirm 整体回滚；</li>
     *   <li>读 {@code before = balance.quantity}（**持锁后**读，快照才可信）；</li>
     *   <li>追加流水（源身份防重；影响行数 0 → 41002 fail-fast）；</li>
     *   <li>余额增量自增（{@code quantity = quantity + ?}，无「读-改-写」窗口）。</li>
     * </ol>
     *
     * @throws ScmBusinessException 41003 / 40485 / 41001 / 41002 / 40921
     */
    public void postPurchaseInbound(PurchaseInventoryContract.InboundFact fact) {
        // Do not silently autocommit the balance and ledger in separate statements.
        // The receipt confirmation owns the transaction; this service must never create one.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Purchase inbound requires the caller's transaction");
        }
        requireFact(fact);

        // 1. 仓库引用守卫（复用既有码 40485，不新造）
        warehouseService.require(fact.warehouseId());

        // 2. 并发安全的首建：冲突目标与部分唯一索引完全匹配（Q11），赢家/输家随后都拿同一行锁
        balanceDao.insertOnConflictDoNothing(
                fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            // 理论上不可达：上一条 INSERT 要么插入成功、要么行已存在。留断言是为了不静默 NPE。
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }

        // 3. Q13 单位不变量：一个 (warehouse, sku) 只允许一个记账单位
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
        }

        // 4. before 必须持锁后读取 —— 否则并发下 before/after 恒等式会失真
        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.PURCHASE_IN.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.PURCHASE_RECEIPT_ITEM.name());
        movement.setSourceDocumentId(fact.receiptId());
        movement.setSourceDocumentItemId(fact.receiptItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        // Q13-附：事实来自收货确认，绝不用 now() / ambient operator
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            // 实时路径下这是不可能发生的数据异常（claim 幂等 + 收货单状态机已挡住）：
            // fail-fast 让整个 confirm 回滚，而不是「静默跳过入库」后返回一个成功结果。
            throw new ScmBusinessException(INVENTORY_DUPLICATE_INBOUND);
        }

        // 5. 持锁后的增量自增（无丢失更新窗口）
        if (balanceDao.incrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // 读路径：可用量探测
    // ------------------------------------------------------------------

    /**
     * 可用量探测（W5 契约的 {@code queryAvailability}）。
     *
     * <p><b>W6 起不再返回 {@code null}</b>：{@code null} 的语义是「库存能力未启用」，
     * 而 W6-1 已真实启用入库，因此能力已上线，该语义退场（与 W5 期间 NoOp 返回 null 的区别
     * 即「能力上线」）。余额行不存在返回 {@code available = 0} —— 那是「确实没有库存」，
     * 不是「没有库存能力」。
     *
     * <p>W6-1 没有占用机制（{@code OrderInventoryContract.reserve/release} 保持零实现零调用），
     * 因此 {@code reserved} 恒为 0；字段保留是为了匹配契约签名。
     */
    public PurchaseInventoryContract.Availability queryAvailability(Long skuId, Long warehouseId) {
        if (skuId == null || warehouseId == null) {
            return new PurchaseInventoryContract.Availability(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        InventoryBalanceEntity balance = balanceDao.selectByWarehouseAndSku(warehouseId, skuId);
        return new PurchaseInventoryContract.Availability(
                balance == null ? BigDecimal.ZERO : balance.getQuantity(),
                BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 入库事实的最小不变量。
     *
     * <p>只断言**事实本身是否可入库**，不重复采购侧的校验（数量口径、超收容差、实重来源
     * 都在 {@code PurchaseReceiptService.confirm} 里已经判过）—— 库存域对数量的唯一要求是
     * 「正的、有单位的、有发生时刻与操作者的增量」。
     */
    private static void requireFact(PurchaseInventoryContract.InboundFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.receiptId() == null
                || fact.receiptItemId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.unit() == null
                || fact.unit().isBlank()
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
    }
}

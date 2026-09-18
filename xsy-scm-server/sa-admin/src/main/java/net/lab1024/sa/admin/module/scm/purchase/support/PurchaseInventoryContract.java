package net.lab1024.sa.admin.module.scm.purchase.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * W5 定义、W6 实现的库存契约。
 *
 * <p>W5 只定义契约并提供一个**不注册为 Bean**的 {@link NoOpPurchaseInventoryContract}，
 * 因此 W5 的收货确认**不产生任何库存事实**。W6-1 交付真实实现
 * （{@code inventory.support.PurchaseInventoryContractImpl}），
 * 唯一调用点是 {@code PurchaseReceiptService.confirm(...)} 内、**同一个事务**里、
 * 采购侧全部写入之后（§4.3 第 15 步）。
 *
 * <p><b>编译期依赖方向</b>：{@code purchase} 侧只依赖本接口，**不 import inventory 模块任何类**；
 * {@code inventory} 侧实现本接口（因此 inventory → purchase 仅限本契约类型）。
 * Spring 在装配期完成接线，purchase → inventory 的编译期依赖为零。
 *
 * <p>设计依据：W5 Target Design §8.2（契约定义）+ §8.5（Q5 修订后的 W6 bootstrap 口径）；
 * W6 Target Design §6（接线）+ Q13-附（{@code occurredAt} / {@code operator} 契约演进）。
 */
public interface PurchaseInventoryContract {

    /**
     * 稳定唯一源键的文档类型常量（Q5）。
     *
     * <p>W6 的库存流水必须持久化 {@code source_document_type = SOURCE_DOCUMENT_TYPE} 与
     * {@code source_document_item_id = InboundFact.receiptItemId}，并据此建立部分唯一索引
     * {@code uk (source_document_type, source_document_item_id) WHERE deleted = FALSE}，
     * 使「历史 backfill / 未来实时 confirm / 重试」三者不可重复入库。
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * 一次「已确认的收货行」所代表的入库事实。
     *
     * <p>W5 只负责产生这个事实；W6 决定它是被 push（本方法）还是 pull（W6 读表）。
     * {@code idempotencyKey} 由 W6 用于防重（W6 侧应有
     * {@code uk (source_document_type, source_document_item_id)} 的部分唯一索引）。
     *
     * @param purchaseOrderId 采购单 id
     * @param receiptId       收货单 id
     * @param receiptItemId   稳定唯一源键的 item 维度：{@code source_document_item_id}（Q5）
     * @param warehouseId     仓库 id（采购单绑定，收货单继承）
     * @param skuId           SKU id
     * @param warehouseCode   仓库编码快照
     * @param warehouseName   仓库名称快照
     * @param skuCode         SKU 编码快照
     * @param skuName         SKU 名称快照
     * @param unit            采购单位快照（{@code purchase_unit_snapshot}）
     * @param quantity        有效数量（{@code received_quantity}）
     * @param unitCost        采购单价快照（来自 {@code purchase_order_item.purchase_price}）
     * @param idempotencyKey  W6 侧防重键
     * @param occurredAt      **W6 contract evolution（Q13-附）**：业务发生时刻，必须传
     *                        {@code receipt.confirmed_at}。库存流水以此作为 {@code occurred_at}，
     *                        与 backfill 回放的历史流水保持同一时间口径 ——
     *                        禁止库存侧用 {@code OffsetDateTime.now()} 替代。
     * @param operator        **W6 contract evolution（Q13-附）**：操作者，必须传
     *                        {@code receipt.operator}。禁止库存侧用 ambient operator 替代。
     */
    record InboundFact(
            Long purchaseOrderId,
            Long receiptId,
            Long receiptItemId,
            Long warehouseId,
            Long skuId,
            String warehouseCode,
            String warehouseName,
            String skuCode,
            String skuName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitCost,
            String idempotencyKey,
            OffsetDateTime occurredAt,
            String operator) {
    }

    /**
     * 可用量探测。
     *
     * <p><b>{@code null} 表示「库存能力未启用」</b>，必须与「可用量为 0」严格区分 ——
     * 与 W3 的 {@code UNPRICED ≠ 0 元}、W4 的 {@code ordered_total_amount 可空} 同一语义纪律。
     *
     * <p><b>W6 起实现方不再返回 {@code null}</b>（能力已上线）：余额行不存在时返回
     * {@code available = 0}。{@code null} 分支保留在契约里，供未来可能出现的新部署形态使用。
     */
    record Availability(BigDecimal available, BigDecimal reserved) {
    }

    /**
     * 入库写入（W6 已接线：{@code PurchaseReceiptService.confirm} 的唯一调用点）。
     *
     * <p>实现方**必须加入调用方事务**（不得另开事务），失败即让收货确认整体回滚。
     */
    void postInbound(InboundFact fact);

    /** 可用量探测；{@code null} = 库存能力未启用（W6 实现方返回非 null，见 {@link Availability}）。 */
    Availability queryAvailability(Long skuId, Long warehouseId);
}

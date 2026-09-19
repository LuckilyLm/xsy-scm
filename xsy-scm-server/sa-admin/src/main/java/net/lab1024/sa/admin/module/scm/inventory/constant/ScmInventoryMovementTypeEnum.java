package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存流水类型（W6 Target Design §2.2）。
 *
 * <p><b>方向编码在类型里</b>：{@code PURCHASE_IN} 即「入」、{@code SALES_OUT} 即「出」，
 * 因此 {@code inventory_movement} 没有独立的 {@code direction} 列，{@code quantity} 恒为正
 * （由 {@code ck_inventory_movement_qty} 在 DB 层强制）。与 reference 的
 * 「direction + 正数」双表达相比少一列，且不可能自相矛盾。
 *
 * <p><b>方向与快照约束必须同步</b>：{@code ck_inventory_movement_snap} 是**方向感知**的
 * （入库 {@code after = before + quantity}，出库 {@code after = before - quantity}）。
 * 新增类型时必须同时：
 * <ol>
 *   <li>扩 {@code ck_inventory_movement_type} 白名单（新迁移，不改 V19）；</li>
 *   <li>若方向与已有类型不同，同步扩 {@code ck_inventory_movement_snap} 的方向分支；</li>
 *   <li>在本枚举加值；</li>
 *   <li>复用同一套「先锁单据、后按 (warehouse_id, sku_id) 升序锁余额」的锁序规则（§8.1）。</li>
 * </ol>
 *
 * <p><b>已实现</b>：W6-1 的 {@code PURCHASE_IN}；出库波次新增 {@code SALES_OUT}；
 * 盘点波次新增 {@code STOCKTAKE_GAIN} / {@code STOCKTAKE_LOSS}；
 * 报损报溢波次新增 {@code LOSS_REPORT} / {@code GAIN_REPORT}；
 * 调拨波次新增 {@code TRANSFER_OUT} / {@code TRANSFER_IN}。规格转换仍待后续波次。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryMovementTypeEnum {

    /** 采购入库：收货确认或仓库二次入库确认时写入（方向 = 入）。 */
    PURCHASE_IN("采购入库", true),

    /** 销售出库：独立出库单确认时写入（方向 = 出）。 */
    SALES_OUT("销售出库", false),

    /**
     * 盘盈：盘点确认时实盘量高于账面量的部分（方向 = 入）。
     *
     * <p>数量是**差异的绝对值**，恒为正 —— 与「方向编码在类型里」的既有纪律一致。
     * 盘盈与盘亏拆成两个类型而不是一个 {@code STOCKTAKE_ADJUST}：
     * 方向必须能从类型本身读出来，否则 {@code ck_inventory_movement_snap} 无法判定
     * {@code after} 该加还是该减。
     */
    STOCKTAKE_GAIN("盘盈", true),

    /** 盘亏：盘点确认时实盘量低于账面量的部分（方向 = 出）。 */
    STOCKTAKE_LOSS("盘亏", false),

    /**
     * 报损：报损单审批通过时写入（方向 = 出）。
     *
     * <p>与 {@code STOCKTAKE_LOSS} 分开而不是复用：两者都减少库存，但**业务含义与追责对象不同**
     * （盘亏是「账实不符」的结果，报损是「对已知损耗的申报」）。合并成一个类型后，
     * 「这个月损耗了多少」就无法从流水里直接读出来，而那正是报损存在的理由。
     */
    LOSS_REPORT("报损", false),

    /** 报溢：报溢单审批通过时写入（方向 = 入）。 */
    GAIN_REPORT("报溢", true),

    /**
     * 调拨转出：调拨单**发出**时写入**源仓**（方向 = 出）。
     *
     * <p>与 {@code SALES_OUT} 分开而不是复用：两者都从仓库扣货，但业务含义不同
     * （销售出库是履约，调拨转出只是换仓）。合并后「这个月跨仓调了多少」就无法从流水读出。
     */
    TRANSFER_OUT("调拨转出", false),

    /** 调拨转入：调拨单**收货**时写入**目标仓**（方向 = 入）。 */
    TRANSFER_IN("调拨转入", true),

    /**
     * 规格转换出：转换单审批通过时写入**源 SKU**（方向 = 出）。
     *
     * <p>与 {@code TRANSFER_OUT} / {@code SALES_OUT} 分开而不是复用：三者都是「出」，
     * 但业务含义不同（调拨是换仓、销售是履约、转换是改规格）。合并后
     * 「这个月拆零消耗了多少」就无法从流水里读出来。
     */
    CONVERT_OUT("规格转换出", false),

    /** 规格转换入：转换单审批通过时写入**目标 SKU**（方向 = 入）。 */
    CONVERT_IN("规格转换入", true);

    /** 持久化到 {@code inventory_movement.movement_type} 的值。 */
    private final String desc;

    /**
     * 方向：{@code true} = 入库（余额增加），{@code false} = 出库（余额减少）。
     *
     * <p>与 {@code ck_inventory_movement_snap} 的方向分支同源 —— 新增类型时必须同步该约束。
     */
    private final boolean inbound;

    /** 该值是否允许写入 {@code inventory_movement.movement_type}（DB CHECK 白名单的同源判定）。 */
    public static boolean isSupported(String value) {
        for (ScmInventoryMovementTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 按持久化值取枚举；未知值返回 {@code null}（调用方自行判定为参数错误）。 */
    public static ScmInventoryMovementTypeEnum of(String value) {
        for (ScmInventoryMovementTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}

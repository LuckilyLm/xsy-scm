package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 库存域错误码（19 个）。
 *
 * <p>设计依据：W6 Target Design §10.2 / 裁决 Q13；出库与预留的码在出库波次追加，
 * 盘点的码在盘点波次追加。
 *
 * <pre>
 * 40486                 NOT_FOUND   1
 * 41001–41003           既有 3 个
 * 41011–41017           出库波次 7 个
 * 41019–41026           盘点波次 8 个
 * </pre>
 *
 * <p><b>为什么是 40486 / 41xxx</b>：2026-09-18 与全域码表核对，全仓 {@code 4xxxx} 已占用
 * {@code 40000–40091 / 40410–40499 / 40910–40999}，其中 {@code 40486} 落在 4048x 段的空档内、
 * {@code 41000+} 完全空闲。与 W1–W5 的全部错误码**零交集**
 * （由 {@code ScmInventoryConstantTest} 门禁强制）。
 *
 * <p><b>410xx 段的实际占用必须现查现用</b>：41004–41007 属 warehouse、41008 属 purchase、
 * 41018 亦属 warehouse，因此库存域只能取 41001–41003 / 41011–41017 / 41019–41026。
 * 不要相信任何注释里写的「本段空闲」—— 那是写下时的状态，会过期。
 *
 * <p><b>刻意不放进本枚举的码</b>：{@code WarehouseErrorCode.WAREHOUSE_NOT_FOUND(40485)} ——
 * 仓库不存在是 warehouse 域的事实，库存域直接复用（AGENTS §9 稳定码纪律：可复用的既有码
 * 不复制语义），这样也不会形成 {@code inventory → warehouse} 之外的反向依赖。
 */
@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ScmErrorCode {

    /**
     * 40486：余额详情按 id 查不到（行不存在或已软删）。
     *
     * <p>只用于**显式按 id 取详情**的端点。列表查询不会因「没有余额行」报错 ——
     * 「某个 SKU 没有库存」是正常状态，不是错误。
     */
    INVENTORY_BALANCE_NOT_FOUND(40486, "库存余额不存在"),

    /**
     * 41001（Q13 单位不变量）：同一 {@code (warehouse_id, sku_id)} 的入库单位与既有余额不一致。
     *
     * <p>这是**显式失败**而不是自动换算：{@code SupplierSku.purchaseUnit} 是 supplier + sku 维度，
     * 同一 SKU 经不同供应商入库时理论上可以是不同单位；静默把「箱」与「kg」相加会得到一个
     * 没有物理意义的余额，而错误只会在未来出库/盘点时以「账实不符」的形式暴露。
     */
    INVENTORY_UNIT_MISMATCH(41001, "该仓库与 SKU 的库存记账单位与本次入库单位不一致，无法直接累加"),

    /**
     * 41002：源身份重复入库。
     *
     * <p>实时 confirm 路径下这属于**不可能发生的数据异常**（claim 幂等 + 收货单状态机已挡住），
     * 因此 fail-fast 暴露问题，**不静默吞掉**（A 源 spec §8.2：不能通过捕获异常后继续写入
     * 来掩盖库存不一致）。backfill 路径下「影响行数 = 0」是预期值，由 backfill 自身区分处理。
     */
    INVENTORY_DUPLICATE_INBOUND(41002, "该来源单据已入库，不能重复入库"),

    /** 41003：入库事实非法（quantity &lt;= 0、unit / occurredAt / operator 快照缺失等）。 */
    INVENTORY_PARAM_INVALID(41003, "库存入库事实不合法"),

    /**
     * 41011：可用量不足。
     *
     * <p>可用量 = {@code quantity - reserved_quantity}。出库与预留都要先过这一关：
     * 出库不能吃掉已被预留的货，预留不能超出可用量。DB 层另有
     * {@code ck_inventory_balance_available} 兜底，本码负责给出可读原因。
     */
    INVENTORY_INSUFFICIENT_AVAILABLE(41011, "可用库存不足（可用量 = 现有量 − 预留量）"),

    /**
     * 41012：出库事实非法（quantity &lt;= 0、warehouseId / skuId / 来源行缺失等）。
     */
    INVENTORY_OUTBOUND_PARAM_INVALID(41012, "库存出库事实不合法"),

    /** 41013：出库单不存在（行不存在或已软删）。 */
    INVENTORY_OUTBOUND_NOT_FOUND(41013, "出库单不存在"),

    /** 41014：出库单当前状态不允许该操作（如已确认还要再改明细）。 */
    INVENTORY_OUTBOUND_STATUS_INVALID(41014, "出库单当前状态不允许该操作"),

    /** 41015：源身份重复出库（同一条出库单行已写过流水）。 */
    INVENTORY_DUPLICATE_OUTBOUND(41015, "该来源单据已出库，不能重复出库"),

    /** 41016：预留事实非法或状态不允许（如已释放还要再释放）。 */
    INVENTORY_RESERVATION_INVALID(41016, "库存预留不合法或当前状态不允许该操作"),

    /** 41017：出库单至少需要一行明细。 */
    INVENTORY_OUTBOUND_EMPTY_ITEMS(41017, "出库单至少需要一条明细"),

    /** 41019：盘点单不存在（行不存在或已软删）。 */
    INVENTORY_STOCKTAKE_NOT_FOUND(41019, "盘点单不存在"),

    /** 41020：盘点单当前状态不允许该操作（如已确认还要再改明细）。 */
    INVENTORY_STOCKTAKE_STATUS_INVALID(41020, "盘点单当前状态不允许该操作"),

    /** 41021：盘点单至少需要一行明细。 */
    INVENTORY_STOCKTAKE_EMPTY_ITEMS(41021, "盘点单至少需要一条明细"),

    /** 41022：盘点事实非法（actualQuantity 为负、warehouseId / skuId / 来源行缺失等）。 */
    INVENTORY_STOCKTAKE_PARAM_INVALID(41022, "库存盘点事实不合法"),

    /**
     * 41023：该 {@code (warehouse, sku)} 没有余额行，无法盘点。
     *
     * <p>记账单位（Q13）只能来自余额行，因此「从未入库过的 SKU」不能在盘点里凭空盘盈 ——
     * 那需要先有入库事实来确定单位。这不是能力缺失，而是刻意不让盘点成为
     * 「绕过入库、凭空造库存」的入口。
     */
    INVENTORY_STOCKTAKE_BALANCE_MISSING(41023, "该仓库与 SKU 尚无库存记录，请先办理入库再盘点"),

    /**
     * 41024（Q10）：盘点调整后数量为负。
     *
     * <p>出现这种组合说明「清点差异」与「确认瞬间账面量」指向了矛盾的事实
     * （例如盘亏量大于确认时的账面量），此时**必须失败**而不是写出负库存 ——
     * 否则 {@code ck_inventory_balance_quantity} 也会在 DB 层拒绝，但错误会难以归因。
     */
    INVENTORY_STOCKTAKE_NEGATIVE_AFTER(41024, "盘点调整后库存数量为负，请核对账面量与实盘量"),

    /**
     * 41025：盘点调整后低于已预留量（可用量为负）。
     *
     * <p>已预留的货不能被盘点吃掉 —— 预留代表对下游（销售订单）的承诺，
     * 盘亏到低于预留量意味着承诺无法兑现，必须显式失败而不是静默破坏
     * {@code ck_inventory_balance_available}。
     */
    INVENTORY_STOCKTAKE_BELOW_RESERVED(41025, "盘点调整后库存低于已预留量，请先释放预留或核对实盘量"),

    /** 41026：源身份重复盘点（同一条盘点明细行已写过流水）。 */
    INVENTORY_DUPLICATE_STOCKTAKE(41026, "该盘点明细行已产生库存流水，不能重复盘点"),

    /**
     * 41027：同一 SKU 在盘点单里出现多次。
     *
     * <p>必须显式拒绝而不是静默去重：重复行会让同一份差异被施加两次，
     * 而结果看起来完全正常（余额确实变了），只是变错了。这类错误只有在
     * 未来对账时才会暴露，所以要在入口挡住。
     */
    INVENTORY_STOCKTAKE_DUPLICATE_SKU(41027, "同一 SKU 在盘点单中只能出现一次");

    private final int code;
    private final String msg;
}

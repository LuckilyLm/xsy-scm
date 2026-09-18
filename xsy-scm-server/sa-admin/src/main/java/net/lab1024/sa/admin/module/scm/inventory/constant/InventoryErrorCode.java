package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 库存域错误码（4 个）。
 *
 * <p>设计依据：W6 Target Design §10.2 / 裁决 Q13。
 *
 * <pre>
 * 40486        NOT_FOUND   1
 * 41001–41003  CONFLICT / BAD_REQUEST  3
 * </pre>
 *
 * <p><b>为什么是 40486 / 41xxx</b>：2026-09-18 与全域码表核对，全仓 {@code 4xxxx} 已占用
 * {@code 40000–40091 / 40410–40499 / 40910–40999}，其中 {@code 40486} 落在 4048x 段的空档内、
 * {@code 41000+} 完全空闲。与 W1–W5 的全部错误码**零交集**
 * （由 {@code InventoryErrorCodeTest} 门禁强制）。
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
    INVENTORY_PARAM_INVALID(41003, "库存入库事实不合法");

    private final int code;
    private final String msg;
}

package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 库存域错误码（45 个）。
 *
 * <p>设计依据：W6 Target Design §10.2 / 裁决 Q13；出库与预留的码在出库波次追加，
 * 盘点、报损报溢、调拨、阈值预警依次追加。
 *
 * <pre>
 * 40486                 NOT_FOUND   1
 * 41001–41003           既有 3 个
 * 41011–41017           出库波次 7 个
 * 41019–41027           盘点波次 9 个
 * 41028–41037           报损报溢波次 10 个
 * 41038–41048           调拨波次 11 个
 * 41049–41052           阈值预警波次 4 个
 * </pre>
 *
 * <p><b>为什么是 40486 / 41xxx</b>：2026-09-18 与全域码表核对，全仓 {@code 4xxxx} 已占用
 * {@code 40000–40091 / 40410–40499 / 40910–40999}，其中 {@code 40486} 落在 4048x 段的空档内、
 * {@code 41000+} 完全空闲。与 W1–W5 的全部错误码**零交集**
 * （由 {@code ScmInventoryConstantTest} 门禁强制）。
 *
 * <p><b>410xx 段的实际占用必须现查现用</b>：41004–41007 属 warehouse、41008 属 purchase、
 * 41009 属 warehouse（调拨波次新增的在途阻塞码）、41018 亦属 warehouse，
 * 因此库存域只能取 41001–41003 / 41011–41017 / 41019–41052。
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

    /**
     * 41003：入库事实非法（quantity &lt;= 0、unit / occurredAt / operator 快照缺失等）。
     */
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

    /**
     * 41013：出库单不存在（行不存在或已软删）。
     */
    INVENTORY_OUTBOUND_NOT_FOUND(41013, "出库单不存在"),

    /**
     * 41014：出库单当前状态不允许该操作（如已确认还要再改明细）。
     */
    INVENTORY_OUTBOUND_STATUS_INVALID(41014, "出库单当前状态不允许该操作"),

    /**
     * 41015：源身份重复出库（同一条出库单行已写过流水）。
     */
    INVENTORY_DUPLICATE_OUTBOUND(41015, "该来源单据已出库，不能重复出库"),

    /**
     * 41016：预留事实非法或状态不允许（如已释放还要再释放）。
     */
    INVENTORY_RESERVATION_INVALID(41016, "库存预留不合法或当前状态不允许该操作"),

    /**
     * 41017：出库单至少需要一行明细。
     */
    INVENTORY_OUTBOUND_EMPTY_ITEMS(41017, "出库单至少需要一条明细"),

    /**
     * 41019：盘点单不存在（行不存在或已软删）。
     */
    INVENTORY_STOCKTAKE_NOT_FOUND(41019, "盘点单不存在"),

    /**
     * 41020：盘点单当前状态不允许该操作（如已确认还要再改明细）。
     */
    INVENTORY_STOCKTAKE_STATUS_INVALID(41020, "盘点单当前状态不允许该操作"),

    /**
     * 41021：盘点单至少需要一行明细。
     */
    INVENTORY_STOCKTAKE_EMPTY_ITEMS(41021, "盘点单至少需要一条明细"),

    /**
     * 41022：盘点事实非法（actualQuantity 为负、warehouseId / skuId / 来源行缺失等）。
     */
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

    /**
     * 41026：源身份重复盘点（同一条盘点明细行已写过流水）。
     */
    INVENTORY_DUPLICATE_STOCKTAKE(41026, "该盘点明细行已产生库存流水，不能重复盘点"),

    /**
     * 41027：同一 SKU 在盘点单里出现多次。
     *
     * <p>必须显式拒绝而不是静默去重：重复行会让同一份差异被施加两次，
     * 而结果看起来完全正常（余额确实变了），只是变错了。这类错误只有在
     * 未来对账时才会暴露，所以要在入口挡住。
     */
    INVENTORY_STOCKTAKE_DUPLICATE_SKU(41027, "同一 SKU 在盘点单中只能出现一次"),

    /**
     * 41028：报损报溢单不存在（行不存在或已软删）。
     */
    INVENTORY_LOSS_GAIN_NOT_FOUND(41028, "报损报溢单不存在"),

    /**
     * 41029：报损报溢单当前状态不允许该操作。
     *
     * <p>只有「待审核」可改 / 可删 / 可审批。已完成的单据已写流水，改它会让账与单对不上；
     * 已驳回的单据必须留痕。参考项目对 update / delete 没有状态守卫，本波次刻意补上。
     */
    INVENTORY_LOSS_GAIN_STATUS_INVALID(41029,
            "报损报溢单当前状态不允许该操作（仅待审核可改、可删、可审批）"),

    /**
     * 41030：报损报溢单至少需要一行明细。
     */
    INVENTORY_LOSS_GAIN_EMPTY_ITEMS(41030, "报损报溢单至少需要一条明细"),

    /**
     * 41031：报损报溢事实非法（quantity &lt;= 0、仓库 / SKU / 来源行缺失等）。
     */
    INVENTORY_LOSS_GAIN_PARAM_INVALID(41031, "报损报溢事实不合法"),

    /**
     * 41032：该 {@code (warehouse, sku)} 没有余额行，无法报损报溢。
     *
     * <p>与盘点的 41023 是**同一类约束**（记账单位只能来自余额行），
     * 但补救动作不同：盘点要「先入库再盘点」，报损报溢要「先入库再报损报溢」。
     * 因此各自保留一个码，让前端提示能给出准确的下一步。
     */
    INVENTORY_LOSS_GAIN_BALANCE_MISSING(41032, "该仓库与 SKU 尚无库存记录，请先办理入库再报损报溢"),

    /**
     * 41033（Q10）：报损后库存数量为负。
     */
    INVENTORY_LOSS_GAIN_NEGATIVE_AFTER(41033, "报损数量超过现有库存，请核对后重填"),

    /**
     * 41034：报损后低于已预留量（可用量为负）。
     *
     * <p>已预留的货代表对下游（销售订单）的承诺，报损不能把它吃掉 ——
     * 否则承诺无法兑现，且 {@code ck_inventory_balance_available} 会在 DB 层拒绝。
     */
    INVENTORY_LOSS_GAIN_BELOW_RESERVED(41034, "报损后库存低于已预留量，请先释放预留或减少报损数量"),

    /**
     * 41035：源身份重复报损报溢（同一条单据行已写过流水）。
     */
    INVENTORY_DUPLICATE_LOSS_GAIN(41035, "该报损报溢明细行已产生库存流水，不能重复审批"),

    /**
     * 41036：同一 SKU 在报损报溢单里出现多次。
     *
     * <p>与盘点的 41027 同一理由：重复行会让同一份数量被调整两次，
     * 而结果看起来完全正常。
     */
    INVENTORY_LOSS_GAIN_DUPLICATE_SKU(41036, "同一 SKU 在报损报溢单中只能出现一次"),

    /**
     * 41037：驳回时必须填写审核意见。
     *
     * <p>驳回是**唯一会把「为什么不行」传达给录单人的渠道**（本波次没有消息通知）。
     * 允许空意见的驳回会让录单人只知道被拒、不知道改什么，只能反复试 ——
     * 那是把沟通成本转移给了最不该承担的人。
     */
    INVENTORY_LOSS_GAIN_REJECT_OPINION_REQUIRED(41037, "驳回时必须填写审核意见，说明驳回原因"),

    /**
     * 41038：调拨单不存在（行不存在或已软删）。
     */
    INVENTORY_TRANSFER_NOT_FOUND(41038, "调拨单不存在"),

    /**
     * 41039：调拨单当前状态不允许该操作。
     *
     * <p>发出仅草稿可做、收货仅在途可做、改/删仅草稿可做。
     * **在途不可取消**：货已物理离开源仓，账上只能靠反向调拨单冲回。
     */
    INVENTORY_TRANSFER_STATUS_INVALID(41039,
            "调拨单当前状态不允许该操作（草稿可改可发可删，在途只能收货）"),

    /**
     * 41040：调拨单至少需要一行明细。
     */
    INVENTORY_TRANSFER_EMPTY_ITEMS(41040, "调拨单至少需要一条明细"),

    /**
     * 41041：调拨事实非法（quantity &lt;= 0、仓库 / SKU / 来源行缺失等）。
     */
    INVENTORY_TRANSFER_PARAM_INVALID(41041, "调拨事实不合法"),

    /**
     * 41042：源仓库与目标仓库相同。
     *
     * <p>那不是调拨，而是把货在同一行余额上来回加减：净效果为零却留下两条流水，
     * 纯属噪声，还会让「本月调拨量」这个指标虚高。
     */
    INVENTORY_TRANSFER_SAME_WAREHOUSE(41042, "源仓库与目标仓库不能相同"),

    /**
     * 41043：源仓可用量不足。
     *
     * <p>可用量 = {@code quantity − reserved_quantity}。调拨转出与销售出库同一口径：
     * 不得让源仓变负，也不得吃掉源仓已预留的货（预留代表对下游的承诺）。
     */
    INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE(41043,
            "源仓库可用库存不足（可用量 = 现有量 − 预留量），请减少调拨数量或先释放预留"),

    /**
     * 41044：目标仓的记账单位与调拨单位不一致。
     *
     * <p>Q13 规定一个 {@code (warehouse, sku)} 只锁一个记账单位，且**不做隐式换算**：
     * 源仓按「箱」记账、目标仓按「kg」记账时，把 10 箱直接加成 10 kg 会得到一个
     * 没有物理意义的余额，而错误只会在未来盘点时以「账实不符」的形式暴露。
     * 换算能力属「单位转换」波次，本波次显式失败。
     */
    INVENTORY_TRANSFER_UNIT_MISMATCH(41044,
            "目标仓库该 SKU 的记账单位与调拨单位不一致，库存不做自动换算：请先统一两仓的采购单位"),

    /**
     * 41045：同一 SKU 在调拨单里出现多次。
     */
    INVENTORY_TRANSFER_DUPLICATE_SKU(41045, "同一 SKU 在调拨单中只能出现一次"),

    /**
     * 41046：源仓没有该 SKU 的余额行，无法发出。
     *
     * <p>转出是「出」方向，与销售出库同一取向：没有余额行 = 从未入库 = 无货可调，
     * **不建零余额行**（只有「入」方向才允许建行）。
     */
    INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING(41046,
            "源仓库该 SKU 尚无库存记录，无货可调：请确认源仓是否入过库"),

    /**
     * 41047：源身份重复调拨（同一条明细行已写过该方向的流水）。
     */
    INVENTORY_DUPLICATE_TRANSFER(41047, "该调拨明细行已产生库存流水，不能重复操作"),

    /**
     * 41048：仓库已停用，不能用于调拨。
     *
     * <p>与采购侧的 40987 同一类规则（「不允许用停用仓库建单」不是仓库域自身的不变量，
     * 所以码留在调用方域）。发出时断言**源仓**启用、收货时断言**目标仓**启用。
     */
    INVENTORY_TRANSFER_WAREHOUSE_DISABLED(41048, "仓库已停用，不能用于新的调拨业务"),

    /**
     * 41049：预警阈值配置不存在（行不存在或已软删）。
     */
    INVENTORY_WARNING_THRESHOLD_NOT_FOUND(41049, "预警阈值配置不存在"),

    /**
     * 41050：该 {@code (仓库, SKU)} 已经有一条有效阈值配置。
     *
     * <p>必须显式拒绝而不是静默覆盖：两条配置会让「按哪条判断」变得没有答案，
     * 而预警本身是给人看的，含糊的预警等于没有预警。
     */
    INVENTORY_WARNING_THRESHOLD_DUPLICATE(41050,
            "该仓库与 SKU 已配置过预警阈值，请直接编辑既有配置"),

    /**
     * 41051：阈值配置非法。
     *
     * <p>三种情形合并为一个码，因为对用户的补救动作是同一个「改一下配置」：
     * ① 上下限都没有填（都没有的配置没有任何判断依据）；
     * ② 任一阈值为负（库存量不可能为负）；
     * ③ 下限大于上限（那会让所有状态都异常，预警失去意义）。
     * 具体是哪一种由前端表单校验先说清楚，后端只做兜底。
     */
    INVENTORY_WARNING_THRESHOLD_INVALID(41051,
            "预警阈值不合法：上下限至少填一个，且都不能为负、下限不得大于上限"),

    /**
     * 41052：SKU 不存在。
     *
     * <p>阈值配置**必须**校验 SKU 存在（其它库存单据不校验，因为它们总是由已存在的
     * SKU 选择器驱动）：配置表是长期驻留的，一条指向不存在 SKU 的配置会永远留在
     * 预警列表里（没有余额 → 数量按 0 计 → 触发下限预警），成为永远清不掉的噪声。
     */
    INVENTORY_WARNING_THRESHOLD_SKU_NOT_FOUND(41052, "SKU 不存在，请选择有效的 SKU"),

    /**
     * 41053：规格转换单不存在（行不存在或已软删）。
     */
    INVENTORY_CONVERSION_NOT_FOUND(41053, "规格转换单不存在"),

    /**
     * 41054：规格转换单当前状态不允许该操作（仅待审核可改、可删、可审批）。
     */
    INVENTORY_CONVERSION_STATUS_INVALID(41054,
            "规格转换单当前状态不允许该操作（仅待审核可改、可删、可审批）"),

    /**
     * 41055：规格转换单至少需要一行明细。
     */
    INVENTORY_CONVERSION_EMPTY_ITEMS(41055, "规格转换单至少需要一条明细"),

    /**
     * 41056：规格转换事实非法（数量非正、仓库 / SKU / 来源行缺失、单位空白等）。
     */
    INVENTORY_CONVERSION_PARAM_INVALID(41056, "规格转换事实不合法"),

    /**
     * 41057：同一行的源 SKU 与目标 SKU 相同。
     *
     * <p>那不是转换，是把货在**同一行余额**上来回加减：净效果为零却留下两条流水，
     * 还会让「本月转换量」虚高。DB 也有 {@code ck_inventory_conversion_item_distinct} 兜底。
     */
    INVENTORY_CONVERSION_SAME_SKU(41057, "源 SKU 与目标 SKU 不能相同"),

    /**
     * 41058：源 SKU 在该仓库没有余额行。
     *
     * <p>转出是「出」方向，与销售出库 / 调拨转出同一取向：没有余额行 = 从未入库 = 无货可转，
     * **不建零余额行**（只有「入」方向才允许建行）。
     */
    INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING(41058,
            "源 SKU 在该仓库尚无库存记录，无货可转：请确认源 SKU 是否入过库"),

    /**
     * 41059：源 SKU 的余额记账单位与单据声明的源单位不一致。
     *
     * <p>Q13 规定一个 {@code (warehouse, sku)} 只锁一个记账单位且**不做隐式换算**。
     * 单位是单据显式声明的（折算关系本身含单位），因此不一致时只能失败，
     * 不能「按声明改记账单位」—— 那会让既有余额的含义漂移。
     */
    INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH(41059,
            "源 SKU 的记账单位与单据声明的源单位不一致，库存不做自动换算"),

    /**
     * 41060：目标 SKU 已有余额行，但其记账单位与单据声明的目标单位不一致。
     *
     * <p>目标 SKU 没有余额行时允许用声明单位建行（入方向）；**已有**余额行则必须一致，
     * 否则会把「箱」与「kg」相加，得到一个没有物理意义的余额。
     */
    INVENTORY_CONVERSION_TARGET_UNIT_MISMATCH(41060,
            "目标 SKU 的记账单位与单据声明的目标单位不一致，库存不做自动换算"),

    /**
     * 41061：源 SKU 可用量不足。
     *
     * <p>可用量 = {@code quantity − reserved_quantity}。转换不得让源 SKU 变负，
     * 也不得吃掉源 SKU 已预留的货（预留代表对下游的承诺）。
     */
    INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE(41061,
            "源 SKU 可用库存不足（可用量 = 现有量 − 预留量），请减少转出数量或先释放预留"),

    /**
     * 41062：源身份重复转换（同一条明细行已写过该方向的流水）。
     */
    INVENTORY_DUPLICATE_CONVERSION(41062, "该转换明细行已产生库存流水，不能重复审批"),

    /**
     * 41063：驳回时必须填写审核意见。
     */
    INVENTORY_CONVERSION_REJECT_OPINION_REQUIRED(41063, "驳回时必须填写审核意见，说明驳回原因"),

    /**
     * 41064：仓库已停用，不能用于新的规格转换。
     */
    INVENTORY_CONVERSION_WAREHOUSE_DISABLED(41064, "仓库已停用，不能用于新的规格转换业务");

    private final int code;
    private final String msg;
}

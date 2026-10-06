package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收单头：一张已签收销售订单一条正常应收，一张已批准退货一条红字应收。
 *
 * <p>
 * <b>没有状态列</b>：已核销额、未核销额、超额核销、结清状态全部由 {@code finance_write_off} 与红字关系<b>读时派生</b>，不在事实行重复存储。
 *
 * <p>
 * <b>没有 seller_id</b>：范围归属在读取时 join {@code sales_order} 获取当前负责人。 {@code customer_id} 是结算对方，属于财务事实本身。
 *
 * <p>
 * <b>本实体没有任何更新路径</b>：应收一经生成即不可改，红冲是新增一条 {@code RED} 行。因此不提供更新路径，避免把未提交字段写回 NULL。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "finance_receivable", autoResultMap = true)
public class FinanceReceivableEntity extends FinanceRecord {

    private String receivableNo;

    /**
     * {@code ScmFinanceReceivableSourceTypeEnum}；与 {@link #entryType} 由 {@code ck_finance_receivable_source_pairing}
     * 配对约束。
     */
    private String sourceType;

    /**
     * 正常 = {@code sales_order.id}；红字 = {@code order_return.id}。
     */
    private Long sourceId;

    /**
     * 销售订单 id；红字也指向被冲订单，便于按单查询。
     */
    private Long orderId;

    private Long customerId;

    private String customerNameSnapshot;

    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    private LocalDate dueDate;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> creditRuleSnapshot;

    /**
     * {@code ScmFinanceEntryTypeEnum} 的 {@code NORMAL / RED}；方向编码在类型里，金额恒为正。
     */
    private String entryType;

    /**
     * 红字必填，指向被冲的正常应收；正常应收必须为 {@code null} （{@code ck_finance_receivable_entry_pairing}）。
     */
    private Long originalReceivableId;

    /**
     * 单头金额 = 明细之和，scale 4、{@code HALF_UP}、恒 &gt; 0；合计为 0 时不生成事实。
     */
    private BigDecimal amount;

    /**
     * 业务事件时点：正常 = 签收 {@code signed_at}，红字 = 退货 {@code approved_at}。 <b>不是写入时刻</b>，禁止用 {@code now()} 替代。
     */
    private OffsetDateTime eventAt;

    /**
     * 红字原因，红字必填非空。
     */
    private String reason;
}

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
 * 签收订单生成正常应收，批准退货生成红字应收。结清状态和已核销额从核销记录实时计算，不单独保存；历史应收只能新增不能修改。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "xsy_finance_receivable", autoResultMap = true)
public class FinanceReceivableEntity extends FinanceRecord {

    private String receivableNo;

    /**
     * {@code ScmFinanceReceivableSourceTypeEnum}；与 {@link #entryType} 由 {@code ck_finance_receivable_source_pairing}
     * 配对约束。
     */
    private String sourceType;

    /**
     * 正常 = {@code xsy_sales_order.id}；红字 = {@code xsy_order_return.id}。
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
     * 单头金额 = 明细之和，4 位小数、{@code HALF_UP}、恒 &gt; 0；合计为 0 时不生成记录。
     */
    private BigDecimal amount;

    /**
     * 业务事件时点：正常 = 签收 {@code signed_at}，红字 = 退货 {@code approved_at}。不是写入时刻，不能用 {@code now()} 替代。
     */
    private OffsetDateTime eventAt;

    /**
     * 红字原因，红字必填非空。
     */
    private String reason;
}

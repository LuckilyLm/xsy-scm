package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 对账结论：渠道账 vs 本地账。
 *
 * <p>
 * 差额**落库**而不是每次现算：对账结论是要被审计的历史事实，不能随之后的交易变动而变。
 * 表上有 CHECK 保证 {@code MATCHED} 当且仅当差额为 0。
 */
@Data
@TableName(value = "payment_reconciliation", autoResultMap = true)
public class PaymentReconciliationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reconciliationNo;

    private String provider;

    private LocalDate bizDate;

    private String status;

    private BigDecimal providerTotal;

    private BigDecimal localTotal;

    private BigDecimal difference;

    private Integer providerCount;

    private Integer localCount;

    /** 参与比对的交易数：渠道侧与本地侧的并集（同一笔两边都有时只算一次）。 */
    private Integer totalCount;

    private Integer matchedCount;

    /** 差异条数。净差额可能为 0 而仍有差异（一正一负），所以平不平看条数而不是看差额。 */
    private Integer differenceCount;

    /** 差异明细：哪些交易只在一边。 */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> detail;

    private OffsetDateTime reconciledAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}

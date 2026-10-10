package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 对账结论（含逐条差异）。 */
@Data
public class PaymentReconciliationVO {

    private Long id;

    private String reconciliationNo;

    private String provider;

    private LocalDate bizDate;

    private String status;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal providerTotal;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal localTotal;

    /** 净差额。<b>它不用于判断平不平</b>：一正一负的差异会让它归零，但账其实不平。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal difference;

    private Integer providerCount;

    private Integer localCount;

    /** 参与比对的交易数（两侧并集）。 */
    private Integer totalCount;

    /** 其中的平账数。 */
    private Integer matchedCount;

    /** <b>判断平不平看这个</b>：差异条数为 0 才是平账。 */
    private Integer differenceCount;

    /** 分类汇总（各类各几条）。 */
    private Map<String, Object> detail;

    private OffsetDateTime reconciledAt;

    private String remark;

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    /** 逐条差异；平账批次为空。 */
    private List<PaymentReconciliationItemVO> items;
}

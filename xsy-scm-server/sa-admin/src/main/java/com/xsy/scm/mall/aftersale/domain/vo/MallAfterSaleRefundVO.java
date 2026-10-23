package com.xsy.scm.mall.aftersale.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 商城售后单的退款结果与原路退回明细。
 *
 * <p>
 * 只含客户可见字段：渠道失败码、内部失败原因、来源交易 id 等内部事实不下发。
 */
@Data
public class MallAfterSaleRefundVO {
    private Long refundId;
    private String refundNo;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal refundAmount;
    /**
     * 正式退款单状态：PENDING / COMPLETED
     */
    private String status;
    /**
     * 实际 Finance 付款或余额返还时间；正式退款单完成但资金未到时为空。
     */
    private OffsetDateTime completedAt;
    /**
     * 原路退回明细：在线支付与余额各退多少、退到哪一步
     */
    private List<Allocation> allocations = new ArrayList<>();

    @Data
    public static class Allocation {
        /**
         * 资金来源：ONLINE（在线支付原路退回）/ BALANCE（退回余额）
         */
        private String method;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal allocatedAmount;
        /**
         * 分摊状态：PENDING / PROCESSING / SUCCEEDED / FAILED
         */
        private String status;
        private OffsetDateTime succeededAt;
    }
}

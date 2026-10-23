package com.xsy.scm.mall.aftersale.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 商城售后单（列表与详情共用）。
 *
 * <p>
 * 状态、金额、进度全部来自正式退货单 / 退款单 / 资金分摊事实，本 VO 只是面向客户的投影，不引入第二套售后状态。
 */
@Data
public class MallAfterSaleVO {
    private Long returnId;
    private String returnNo;
    private Long orderId;
    private String orderNo;
    /**
     * 正式退货单状态：PENDING / APPROVED / REJECTED / CANCELLED
     */
    private String status;
    /**
     * 面向客户的展示分类：REVIEWING / REFUNDING / REFUNDED / REJECTED / CANCELLED
     */
    private String displayStatus;
    private String reason;
    /**
     * 驳回原因（仅被驳回时有值）；客户撤销不留处理意见
     */
    private String decisionReason;
    /**
     * 申请金额（预估）：申请数量 × 锁定价汇总，核定金额以审核为准
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal requestedAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal approvedAmount;
    /**
     * 是否允许客户撤销：仅待审核可撤销，前端据此置灰
     */
    private Boolean cancelable;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime rejectedAt;
    private OffsetDateTime cancelledAt;
    /**
     * 明细行数
     */
    private Integer itemCount;
    /**
     * 列表卡片用的商品缩略图，最多 3 张；取不到图的商品跳过
     */
    private List<String> itemThumbnails = new ArrayList<>();
    /**
     * 明细行，仅详情返回
     */
    private List<MallAfterSaleItemVO> items = new ArrayList<>();
    /**
     * 退款结果与原路退回明细，仅详情返回；退款净额为 0 无退款单时为 null
     */
    private MallAfterSaleRefundVO refund;
    /**
     * 售后进度节点（已提交 → 审核 → 退款），仅详情返回；被驳回 / 已撤销到对应节点即止
     */
    private List<MallOrderProgressNodeVO> progress = new ArrayList<>();
}

package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 商城订单（列表与详情共用）。
 *
 * <p>
 * 金额、状态、履约进度全部来自正式销售订单与配送事实，本 VO 只是面向客户的投影，不引入第二套订单状态。
 */
@Data
public class MallOrderVO {
    private Long orderId;
    private String orderNo;
    /** 正式销售订单状态：DRAFT / PENDING / CONFIRMED / CANCELLED */
    private String status;
    /** 面向客户的展示分类：PENDING / PREPARING / DELIVERING / COMPLETED / CANCELLED */
    private String displayStatus;
    /** 订单级履约状态（来自生效中的线路订单）；未排车时为 null */
    private String fulfillmentStatus;
    /** 是否允许客户取消：由订单状态机与履约事实共同决定，前端据此置灰 */
    private Boolean cancelable;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedTotalAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementTotalAmount;
    private OffsetDateTime expectDeliveryTime;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private String cancelReason;
    private OffsetDateTime signedAt;
    private String signReason;
    private String receiverName;
    private String receiverPhone;
    private String address;
    /** 明细行数 */
    private Integer itemCount;
    /** 列表卡片用的商品缩略图，最多 3 张；取不到图的商品跳过 */
    private List<String> itemThumbnails = new ArrayList<>();
    private List<MallOrderItemVO> items = new ArrayList<>();
    /** 真实处理记录，仅详情返回 */
    private List<MallOrderTimelineVO> timeline = new ArrayList<>();
    /** 配送信息（线路 / 司机 / 发车 / 完成），仅详情返回；未排车时为 null */
    private MallOrderDeliveryVO delivery;
    /** 配送进度节点（已提交 → 已接单 → 已发车 → 已签收），仅详情返回；已取消订单只到「已取消」 */
    private List<MallOrderProgressNodeVO> progress = new ArrayList<>();
}

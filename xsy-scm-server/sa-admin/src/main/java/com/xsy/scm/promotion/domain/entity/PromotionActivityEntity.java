package com.xsy.scm.promotion.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 营销活动。
 *
 * <p>
 * 活动是<b>版本化的规则</b>：{@code version} 每次编辑自增，订单优惠冻结时把它一起记下来， 因此「这单当时按哪一版算的」永远可查 —— 只存活动 id 的话，改一次活动就会让历史订单的 优惠金额无法解释。
 */
@Data
@TableName(value = "promotion_activity", autoResultMap = true)
public class PromotionActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String activityCode;

    private String activityName;

    /** {@code FULL_REDUCE} / {@code DISCOUNT} / {@code FULL_GIFT}。 */
    private String activityType;

    /** 互斥组：同组内不可叠加；{@code null} 表示不参与互斥判定。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exclusiveGroup;

    /** 同组内取优先级最高的一条；相同优先级按 id 升序，保证结果可复现。 */
    private Integer priority;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    /** {@code DRAFT} / {@code ACTIVE} / {@code STOPPED}。 */
    private String status;

    /** 受控规则内容：门槛金额、减免金额或折扣率、赠品 SKU 等。 */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> rule;

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

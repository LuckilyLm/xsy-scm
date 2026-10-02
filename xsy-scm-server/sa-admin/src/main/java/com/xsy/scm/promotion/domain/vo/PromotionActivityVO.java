package com.xsy.scm.promotion.domain.vo;

import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class PromotionActivityVO {

    private Long id;

    private String activityCode;

    private String activityName;

    private String activityType;

    /** 类型展示名（服务端填，前端不硬编码字典）。 */
    private String activityTypeLabel;

    private String exclusiveGroup;

    private Integer priority;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    private String status;

    private Map<String, Object> rule;

    private String remark;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public static PromotionActivityVO of(PromotionActivityEntity row, String typeLabel) {
        PromotionActivityVO vo = new PromotionActivityVO();
        vo.setId(row.getId());
        vo.setActivityCode(row.getActivityCode());
        vo.setActivityName(row.getActivityName());
        vo.setActivityType(row.getActivityType());
        vo.setActivityTypeLabel(typeLabel);
        vo.setExclusiveGroup(row.getExclusiveGroup());
        vo.setPriority(row.getPriority());
        vo.setValidFrom(row.getValidFrom());
        vo.setValidTo(row.getValidTo());
        vo.setStatus(row.getStatus());
        vo.setRule(row.getRule());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }
}

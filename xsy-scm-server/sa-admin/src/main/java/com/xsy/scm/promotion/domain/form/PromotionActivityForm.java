package com.xsy.scm.promotion.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 活动新建 / 编辑。
 *
 * <p>
 * {@code rule} 收成 {@code Map}：具体键由活动类型决定，交给服务端的规则校验器统一回答
 * 「哪些键是这类活动允许的」，而不是让 Jackson 在反序列化阶段抛一个位置不明的 400。
 */
@Data
public class PromotionActivityForm {

    private Long id;

    @NotBlank(message = "活动编码不能为空")
    @Size(max = 64, message = "活动编码不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "活动编码只能包含字母、数字、下划线与连字符")
    private String activityCode;

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 150, message = "活动名称不能超过150个字符")
    private String activityName;

    @NotBlank(message = "活动类型不能为空")
    @ScmEnumValue(enumClass = ScmPromotionActivityTypeEnum.class, message = "活动类型无效")
    private String activityType;

    @Size(max = 64, message = "互斥组不能超过64个字符")
    private String exclusiveGroup;

    @Min(value = 0, message = "优先级不能小于0")
    private Integer priority;

    @NotNull(message = "生效时间不能为空")
    private OffsetDateTime validFrom;

    @NotNull(message = "失效时间不能为空")
    private OffsetDateTime validTo;

    @NotNull(message = "规则内容不能为空")
    private Map<String, Object> rule;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    private Integer version;
}

package com.xsy.scm.promotion.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class PromotionActivityQueryForm extends PageParam {

    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmPromotionActivityTypeEnum.class, message = "活动类型无效")
    private String activityType;

    @Size(max = 16, message = "状态不能超过16个字符")
    private String status;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Max(value = 100, message = "每页条数不能超过100")
    @Min(value = 1, message = "每页条数必须至少为1")
    public Long getPageSize() {
        return super.getPageSize();
    }
}

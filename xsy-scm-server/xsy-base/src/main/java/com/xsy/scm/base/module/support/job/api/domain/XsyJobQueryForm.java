package com.xsy.scm.base.module.support.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import com.xsy.scm.base.module.support.job.constant.XsyJobTriggerTypeEnum;
import org.hibernate.validator.constraints.Length;

/**
 * 定时任务 分页查询
 *
 */
@Data
public class XsyJobQueryForm extends PageParam {

    @Schema(description = "搜索词|可选")
    @Length(max = 50, message = "搜索词最多50字符")
    private String searchWord;

    @SchemaEnum(desc = "触发类型", value = XsyJobTriggerTypeEnum.class)
    @CheckEnum(value = XsyJobTriggerTypeEnum.class, message = "触发类型错误")
    private String triggerType;

    @Schema(description = "是否启用|可选")
    private Boolean enabledFlag;

    @Schema(description = "是否删除|可选")
    private Boolean deletedFlag;
}

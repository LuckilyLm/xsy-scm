package com.xsy.scm.base.module.support.config.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;
import org.hibernate.validator.constraints.Length;

/**
 * 分页查询 系统配置
 *
 */
@Data
public class ConfigQueryForm extends PageParam {

    @Schema(description = "参数KEY")
    @Length(max = 50, message = "参数Key最多50字符")
    private String configKey;
}

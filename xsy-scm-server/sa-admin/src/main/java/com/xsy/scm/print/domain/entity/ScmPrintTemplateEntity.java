package com.xsy.scm.print.domain.entity;

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
 * 打印模板配置（按单据类型 + 编码）。
 *
 * <p>
 * {@code model} 是受控模型（见 {@code ScmPrintTemplateModel}），存 JSONB；
 * 模板只保存字段 key，不保存标签、不保存 HTML、不保存任何授权信息。
 */
@Data
@TableName(value = "scm_print_template", autoResultMap = true)
public class ScmPrintTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentType;

    private String templateCode;

    private String templateName;

    private Boolean defaultFlag;

    private Boolean enabledFlag;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> model;

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

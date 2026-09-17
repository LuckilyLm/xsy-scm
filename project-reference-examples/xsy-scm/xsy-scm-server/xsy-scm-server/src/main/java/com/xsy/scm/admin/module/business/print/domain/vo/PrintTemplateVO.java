package com.xsy.scm.admin.module.business.print.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打印模板 返回对象
 *
 * @author xsy-scm
 */
@Data
public class PrintTemplateVO {

    @Schema(description = "主键ID")
    private Long templateId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "业务类型：1 采购单，2 发货单，3 分拣小票，4 询价报价单")
    private Integer bizType;

    @Schema(description = "模板内容")
    private String content;

    @Schema(description = "纸张规格")
    private String paperSize;

    @Schema(description = "是否默认模板：0 否，1 是")
    private Integer defaultFlag;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

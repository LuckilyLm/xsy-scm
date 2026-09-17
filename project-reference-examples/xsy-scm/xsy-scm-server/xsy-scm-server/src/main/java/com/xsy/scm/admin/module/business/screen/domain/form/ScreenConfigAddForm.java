package com.xsy.scm.admin.module.business.screen.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据大屏配置 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ScreenConfigAddForm {

    @Schema(description = "大屏编码：1 经营，2 库存，3 采购，4 分拣绩效，5 配送，6 溯源")
    @NotNull(message = "大屏编码不能为空")
    private Integer screenCode;

    @Schema(description = "大屏名称")
    private String screenName;

    @Schema(description = "布局与指标配置（JSON）")
    private String layoutJson;

    @Schema(description = "刷新间隔（秒）")
    private Integer refreshInterval;

    @Schema(description = "排序维度")
    private String sortField;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}

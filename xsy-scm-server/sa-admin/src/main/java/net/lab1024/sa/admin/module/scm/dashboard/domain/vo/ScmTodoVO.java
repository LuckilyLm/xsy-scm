package net.lab1024.sa.admin.module.scm.dashboard.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 业务待办卡片：只读数量 + 跳转路由，不复制任何业务明细。
 */
@Data
public class ScmTodoVO {

    @Schema(description = "卡片标识")
    private String key;

    @Schema(description = "卡片名称")
    private String label;

    @Schema(description = "待处理数量；有权但无任务时为 0，无权时整卡省略")
    private Long count;

    @Schema(description = "点击跳转的前端路由（含与计数一致的筛选条件）")
    private String route;
}
